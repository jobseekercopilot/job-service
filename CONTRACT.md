# API Contract: job-service ↔ reed-gateway

## Overview
This document defines the API contract between the **job-service** and the downstream **reed-gateway**. The job-service receives nested search criteria from the job-finder-gateway, transforms them, and calls the reed-gateway to perform external job searches via Reed.co.uk.

---

## 1. job-service → reed-gateway Request

### Endpoint
```
POST http://reed-gateway/api/jobs/external-search
```

### Headers
| Header | Type | Required | Description |
|--------|------|----------|-------------|
| `X-User-Id` | String | Yes | The authenticated user's ID for context and audit |

### Request Body
The job-service transforms the nested criteria from job-finder-gateway into this format:

```json
{
  "keywords": ["Senior Backend Engineer", "Tech Lead", "Java"],
  "location": "London",
  "distance": 25,
  "employmentType": ["FULL_TIME", "CONTRACT"],
  "salaryMin": 120000,
  "salaryMax": 180000,
  "currency": "USD",
  "page": 1,
  "pageSize": 20
}
```

### Field Descriptions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `keywords` | Array<String> | Yes | Job titles and skills to search for (derived from desiredRoles + skills) |
| `location` | String | Yes | Primary location for job search (first location from aspirations.locations) |
| `distance` | Integer | No | Search radius in miles from location (default: 25) |
| `employmentType` | Array<String> | No | Accepted employment types (FULL_TIME, PART_TIME, CONTRACT, TEMPORARY) |
| `salaryMin` | Integer | No | Minimum annual salary expectation |
| `salaryMax` | Integer | No | Maximum annual salary expectation |
| `currency` | String | No | Currency code (USD, EUR, GBP) - defaults to GBP for UK searches |
| `page` | Integer | No | Page number for pagination (default: 1) |
| `pageSize` | Integer | No | Number of results per page (default: 20, max: 100) |

---

## 2. reed-gateway → job-service Response

### Success Response (200 OK)
```json
{
  "jobs": [
    {
      "id": "reed-job-12345",
      "title": "Senior Backend Engineer",
      "company": "TechCorp Inc.",
      "location": "London, UK",
      "salary": {
        "min": 140000,
        "max": 170000,
        "currency": "GBP"
      },
      "employmentType": "FULL_TIME",
      "postedDate": "2024-01-15T10:30:00Z",
      "description": "We are looking for a Senior Backend Engineer...",
      "url": "https://www.reed.co.uk/jobs/senior-backend-engineer-12345",
      "matchScore": 0.92
    }
  ],
  "totalResults": 42,
  "page": 1,
  "pageSize": 20
}
```

### Field Descriptions - Job Object

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique job identifier from Reed.co.uk |
| `title` | String | Job title |
| `company` | String | Company name |
| `location` | String | Job location |
| `salary` | Object | Salary information with min, max, and currency |
| `employmentType` | String | Employment type (FULL_TIME, PART_TIME, CONTRACT, TEMPORARY) |
| `postedDate` | String (ISO 8601) | Date job was posted |
| `description` | String | Full job description |
| `url` | String | Direct URL to job posting on Reed.co.uk |
| `matchScore` | Number | Match score (0.0 to 1.0) indicating relevance to search criteria |

### Field Descriptions - Salary Object

| Field | Type | Description |
|-------|------|-------------|
| `min` | Integer | Minimum salary (annual) |
| `max` | Integer | Maximum salary (annual) |
| `currency` | String | Currency code (GBP, EUR, USD) |

### Field Descriptions - Pagination

| Field | Type | Description |
|-------|------|-------------|
| `jobs` | Array<Job> | Array of job results |
| `totalResults` | Integer | Total number of matching jobs |
| `page` | Integer | Current page number |
| `pageSize` | Integer | Number of results per page |

---

## 3. Error Responses

### 400 Bad Request
Returned when the job-service sends invalid parameters.

```json
{
  "error": "INVALID_REQUEST",
  "message": "Invalid parameters: distance must be between 1 and 100"
}
```

### 401 Unauthorized
Returned when X-User-Id header is missing or invalid.

```json
{
  "error": "UNAUTHORIZED",
  "message": "Missing or invalid X-User-Id header"
}
```

### 422 Unprocessable Entity
Returned when search parameters are valid but no results are found.

```json
{
  "error": "NO_RESULTS",
  "message": "No jobs found matching the search criteria",
  "jobs": [],
  "totalResults": 0
}
```

### 503 Service Unavailable
Returned when Reed.co.uk API is unreachable or experiencing issues.

```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "External job search service is temporarily unavailable"
}
```

---

## 4. Data Transformation: job-finder-gateway → job-service → reed-gateway

### Step 1: job-finder-gateway sends nested format
```json
{
  "aspirations": {
    "desiredRoles": ["Senior Backend Engineer", "Tech Lead"],
    "industries": ["FinTech", "SaaS"],
    "salaryExpectation": {
      "min": 120000,
      "max": 180000,
      "currency": "USD"
    },
    "locations": ["Remote", "London", "New York"]
  },
  "workPreferences": {
    "employmentType": ["FULL_TIME", "CONTRACT"],
    "remotePreference": "HYBRID",
    "companySize": ["50-200", "200-1000"],
    "culture": ["Innovative", "Collaborative"]
  }
}
```

### Step 2: job-service transforms to reed-gateway format
```json
{
  "keywords": ["Senior Backend Engineer", "Tech Lead"],
  "location": "London",
  "distance": 25,
  "employmentType": ["FULL_TIME", "CONTRACT"],
  "salaryMin": 120000,
  "salaryMax": 180000,
  "currency": "USD",
  "page": 1,
  "pageSize": 20
}
```

### Transformation Rules

| Source (job-finder-gateway) | Target (reed-gateway) | Rule |
|----------------------------|----------------------|------|
| `aspirations.desiredRoles` | `keywords` | Map directly, combine with skills if available |
| `aspirations.locations[0]` | `location` | Use first location as primary search location |
| `aspirations.salaryExpectation.min` | `salaryMin` | Map directly |
| `aspirations.salaryExpectation.max` | `salaryMax` | Map directly |
| `aspirations.salaryExpectation.currency` | `currency` | Map directly |
| `workPreferences.employmentType` | `employmentType` | Map directly |
| N/A | `distance` | Default to 25 miles |
| N/A | `page` | Default to 1 |
| N/A | `pageSize` | Default to 20 |

---

## 5. reed-gateway Responsibilities

The reed-gateway is the **only service permitted** to handle external API integration with Reed.co.uk. It must:

1. **Authenticate with Reed.co.uk**: Manage API keys and OAuth tokens
2. **Transform to Reed API format**: Convert our standard format to Reed.co.uk API parameters
3. **Handle Reed-specific logic**: Rate limiting, result normalization, error mapping
4. **Normalize responses**: Convert Reed.co.uk job data to our standard Job format
5. **Implement caching**: Cache frequent searches to reduce API calls
6. **Monitor Reed API health**: Track uptime and response times

### Reed.co.uk API Integration Notes
- Base URL: `https://www.reed.co.uk/api/1.0/jobs/` (internal to reed-gateway)
- Authentication: API key in Basic Auth header
- Rate limits: Respect Reed.co.uk rate limiting (typically 100 requests/minute)
- Data normalization: Map Reed fields to our standard Job DTO

---

## 6. Error Handling Strategy

### job-service Error Handling

| Scenario | HTTP Status | Error Code | Message |
|----------|-------------|------------|---------|
| Missing required fields (keywords, location) | 400 | INVALID_REQUEST | "Missing required field: {fieldName}" |
| Invalid parameters (distance > 100, page < 1) | 400 | INVALID_REQUEST | "Invalid parameters: {details}" |
| Missing X-User-Id header | 401 | UNAUTHORIZED | "Missing X-User-Id header" |
| reed-gateway unreachable (connection timeout) | 503 | SERVICE_UNAVAILABLE | "Job search service is temporarily unavailable" |
| reed-gateway returns 503 | 503 | SERVICE_UNAVAILABLE | "Job search service is temporarily unavailable" |
| No results found | 200/422 | NO_RESULTS | "No jobs found matching the search criteria" |

### Retry Policy
- **Connection timeouts**: 3 retries with exponential backoff (1s, 2s, 4s)
- **503 errors**: Do not retry, propagate immediately
- **429 errors**: Respect Retry-After header if present

---

## 7. Communication Flow

```
job-finder-gateway
    │
    │ POST /api/jobs/search (nested format)
    │ X-User-Id: {userId}
    │
    ▼
job-service
    │
    │ 1. Validate request
    │ 2. Transform nested → flat reed-gateway format
    │ 3. Add X-User-Id header
    │
    │ POST http://reed-gateway/api/jobs/external-search
    │ X-User-Id: {userId}
    │
    ▼
reed-gateway
    │
    │ 1. Validate X-User-Id
    │ 2. Transform to Reed.co.uk API format
    │ 3. Call Reed.co.uk API
    │ 4. Normalize response
    │
    ▼
Reed.co.uk API
```

---

## 8. Configuration

### job-service application.yml
```yaml
server:
  port: ${SERVER_PORT:8082}

services:
  reed-gateway:
    url: ${REED_GATEWAY_URL:http://localhost:8083}
    connect-timeout: 5000
    read-timeout: 10000

job-search:
  default-distance: 25
  default-page: 1
  default-page-size: 20
  max-page-size: 100
  max-distance: 100
```

### Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8082 | Job service server port |
| `REED_GATEWAY_URL` | http://localhost:8083 | Reed gateway base URL |
| `REED_GATEWAY_CONNECT_TIMEOUT` | 5000 | Connection timeout in ms |
| `REED_GATEWAY_READ_TIMEOUT` | 10000 | Read timeout in ms |

---

## 9. Constraints & Principles

### job-service Constraints
- **No direct Reed.co.uk API calls**: All external calls must go through reed-gateway
- **Thin service**: Minimal business logic, focus on transformation and orchestration
- **Stateless**: No session state maintained between requests
- **Idempotent**: Same request produces same results (within Reed API constraints)

### reed-gateway Constraints
- **Single responsibility**: Only service allowed to integrate with Reed.co.uk
- **API versioning**: Must support Reed.co.uk API versioning
- **Rate limiting**: Implement client-side rate limiting to respect Reed.co.uk limits
- **Caching**: Cache results to reduce external API calls and improve performance
- **Monitoring**: Track Reed API health, response times, and error rates

---

## 10. Version History
- v1.0.0 (2024-01-15): Initial contract definition for job-service ↔ reed-gateway