# Job Service

A Spring Boot microservice for job searching that integrates with the Reed Gateway API to provide job search functionality.

## Overview

The Job Service is a RESTful API that enables job seekers to search for job opportunities using the Reed Gateway API. It provides a clean interface for submitting job search criteria and retrieving matching job listings.

## Features

- Job search functionality via Reed Gateway API
- RESTful API endpoints for job searching
- Spring Boot 3.2.0 with Java 17
- Lombok for reduced boilerplate code
- Actuator endpoints for monitoring and health checks
- Error handling with standardized error responses

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Maven** - Build and dependency management
- **Lombok 1.18.32** - Code generation
- **Spring Web** - REST API framework
- **Spring Actuator** - Monitoring and metrics

## Project Structure

```
job-service/
├── src/main/java/com/jobseekercopilot/jobservice/
│   ├── JobServiceApplication.java          # Main application class
│   ├── client/
│   │   └── ReedGatewayClient.java          # Reed Gateway API client
│   ├── config/
│   │   └── RestTemplateConfig.java         # REST template configuration
│   ├── controller/
│   │   └── JobSearchController.java        # REST API endpoints
│   ├── model/dto/
│   │   ├── Aspirations.java                # Aspirations DTO
│   │   ├── ErrorResponse.java              # Error response DTO
│   │   ├── Job.java                        # Job DTO
│   │   ├── JobSalary.java                  # Job salary DTO
│   │   ├── JobSearchRequest.java           # Job search request DTO
│   │   ├── ReedJobSearchRequest.java       # Reed API request DTO
│   │   ├── ReedJobSearchResponse.java      # Reed API response DTO
│   │   ├── SalaryExpectation.java          # Salary expectation DTO
│   │   └── WorkPreferences.java            # Work preferences DTO
│   └── service/
│       └── JobSearchService.java           # Business logic layer
├── src/main/resources/
│   └── application.yml                     # Application configuration
├── pom.xml                                 # Maven configuration
└── README.md                               # This file
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Git

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/mcgeeverbernard1992/job-service.git
   cd job-service
   ```

2. Build the project:
   ```bash
   mvn clean install
   ```

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The service will start on `http://localhost:8080`

## API Endpoints

### Search Jobs

**Endpoint:** `POST /api/jobs/search`

**Request Body:**
```json
{
  "keywords": "software engineer",
  "location": "London",
  "minimumSalary": 50000,
  "maximumSalary": 80000,
  "jobType": "PERMANENT"
}
```

**Response:**
Returns a list of matching job opportunities from the Reed Gateway API.

## Configuration

The application can be configured via `src/main/resources/application.yml`. Key configurations include:

- Server port
- Reed Gateway API credentials
- Actuator endpoints

## Building for Production

To create an executable JAR file:

```bash
mvn clean package
```

The JAR file will be created in the `target/` directory.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

For questions or support, please open an issue on GitHub.

## Acknowledgments

- Built with Spring Boot
- Job data provided by Reed Gateway API