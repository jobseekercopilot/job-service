package com.jobseekercopilot.jobservice.model.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Job {
    public static final String CURRENT_CANONICAL_SCHEMA_VERSION = "2.0";

    private String canonicalSchemaVersion =
            CURRENT_CANONICAL_SCHEMA_VERSION;
    private String id;
    private String canonicalJobId;
    private String provider;
    private String primarySource;
    private String externalJobId;
    private String title;
    private String jobTitle;
    private String company;
    private String companyName;
    private String location;
    private CanonicalLocation canonicalLocation;
    private JobSalary salary;
    private String employmentType;
    private EmploymentTypeCode employmentTypeCode =
            EmploymentTypeCode.UNKNOWN;
    private String contractType;
    private ContractTypeCode contractTypeCode = ContractTypeCode.UNKNOWN;
    private WorkplaceTypeCode workplaceType = WorkplaceTypeCode.UNKNOWN;
    private String category;
    private String postedDate;
    private String postedAt;
    private String expiresAt;
    private OffsetDateTime postedAtUtc;
    private OffsetDateTime expiresAtUtc;
    private OffsetDateTime applicationDeadlineAtUtc;
    private Double distanceMiles;
    private Boolean remote;
    private String description;
    private String url;
    private String sourceUrl;
    private List<JobSourceReference> sources = new ArrayList<>();
    private List<JobSkill> skills = new ArrayList<>();
    private JobExperience experience = new JobExperience();
    private List<JobFieldProvenance> fieldProvenance = new ArrayList<>();
    private Double matchScore;
    private String applicationStatus;
    private UUID applicationId;
    private String cvDocumentId;
    private String coverLetterDocumentId;
    private LocalDateTime appliedAt;
    private LocalDateTime applicationUpdatedAt;

    public Job() {
    }

    public String getCanonicalSchemaVersion() {
        return canonicalSchemaVersion;
    }

    public void setCanonicalSchemaVersion(String canonicalSchemaVersion) {
        this.canonicalSchemaVersion =
                canonicalSchemaVersion == null
                                || canonicalSchemaVersion.isBlank()
                        ? CURRENT_CANONICAL_SCHEMA_VERSION
                        : canonicalSchemaVersion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCanonicalJobId() {
        return canonicalJobId;
    }

    public void setCanonicalJobId(String canonicalJobId) {
        this.canonicalJobId = canonicalJobId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getPrimarySource() {
        return primarySource;
    }

    public void setPrimarySource(String primarySource) {
        this.primarySource = primarySource;
    }

    public String getExternalJobId() {
        return externalJobId;
    }

    public void setExternalJobId(String externalJobId) {
        this.externalJobId = externalJobId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public CanonicalLocation getCanonicalLocation() {
        return canonicalLocation;
    }

    public void setCanonicalLocation(CanonicalLocation canonicalLocation) {
        this.canonicalLocation = canonicalLocation;
    }

    public JobSalary getSalary() {
        return salary;
    }

    public void setSalary(JobSalary salary) {
        this.salary = salary;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public EmploymentTypeCode getEmploymentTypeCode() {
        return employmentTypeCode;
    }

    public void setEmploymentTypeCode(
            EmploymentTypeCode employmentTypeCode) {
        this.employmentTypeCode = employmentTypeCode == null
                ? EmploymentTypeCode.UNKNOWN
                : employmentTypeCode;
    }

    public String getContractType() {
        return contractType;
    }

    public void setContractType(String contractType) {
        this.contractType = contractType;
    }

    public ContractTypeCode getContractTypeCode() {
        return contractTypeCode;
    }

    public void setContractTypeCode(ContractTypeCode contractTypeCode) {
        this.contractTypeCode = contractTypeCode == null
                ? ContractTypeCode.UNKNOWN
                : contractTypeCode;
    }

    public WorkplaceTypeCode getWorkplaceType() {
        return workplaceType;
    }

    public void setWorkplaceType(WorkplaceTypeCode workplaceType) {
        this.workplaceType = workplaceType == null
                ? WorkplaceTypeCode.UNKNOWN
                : workplaceType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPostedDate() {
        return postedDate;
    }

    public void setPostedDate(String postedDate) {
        this.postedDate = postedDate;
    }

    public String getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(String postedAt) {
        this.postedAt = postedAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getPostedAtUtc() {
        return postedAtUtc;
    }

    public void setPostedAtUtc(OffsetDateTime postedAtUtc) {
        this.postedAtUtc = postedAtUtc;
    }

    public OffsetDateTime getExpiresAtUtc() {
        return expiresAtUtc;
    }

    public void setExpiresAtUtc(OffsetDateTime expiresAtUtc) {
        this.expiresAtUtc = expiresAtUtc;
    }

    public OffsetDateTime getApplicationDeadlineAtUtc() {
        return applicationDeadlineAtUtc;
    }

    public void setApplicationDeadlineAtUtc(
            OffsetDateTime applicationDeadlineAtUtc) {
        this.applicationDeadlineAtUtc = applicationDeadlineAtUtc;
    }

    public Double getDistanceMiles() {
        return distanceMiles;
    }

    public void setDistanceMiles(Double distanceMiles) {
        this.distanceMiles = distanceMiles;
    }

    public Boolean getRemote() {
        return remote;
    }

    public void setRemote(Boolean remote) {
        this.remote = remote;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public List<JobSourceReference> getSources() {
        return sources;
    }

    public void setSources(List<JobSourceReference> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    public List<JobSkill> getSkills() {
        return skills;
    }

    public void setSkills(List<JobSkill> skills) {
        this.skills = skills == null ? new ArrayList<>() : skills;
    }

    public JobExperience getExperience() {
        return experience;
    }

    public void setExperience(JobExperience experience) {
        this.experience = experience == null
                ? new JobExperience()
                : experience;
    }

    public List<JobFieldProvenance> getFieldProvenance() {
        return fieldProvenance;
    }

    public void setFieldProvenance(
            List<JobFieldProvenance> fieldProvenance) {
        this.fieldProvenance = fieldProvenance == null
                ? new ArrayList<>()
                : fieldProvenance;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }

    public String getApplicationStatus() {
        return applicationStatus;
    }

    public void setApplicationStatus(String applicationStatus) {
        this.applicationStatus = applicationStatus;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public String getCvDocumentId() {
        return cvDocumentId;
    }

    public void setCvDocumentId(String cvDocumentId) {
        this.cvDocumentId = cvDocumentId;
    }

    public String getCoverLetterDocumentId() {
        return coverLetterDocumentId;
    }

    public void setCoverLetterDocumentId(String coverLetterDocumentId) {
        this.coverLetterDocumentId = coverLetterDocumentId;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public LocalDateTime getApplicationUpdatedAt() {
        return applicationUpdatedAt;
    }

    public void setApplicationUpdatedAt(LocalDateTime applicationUpdatedAt) {
        this.applicationUpdatedAt = applicationUpdatedAt;
    }
}
