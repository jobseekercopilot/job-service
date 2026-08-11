package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.generated.nhsjobsgateway.api.NhsJobsApi;
import com.jobseekercopilot.generated.nhsjobsgateway.model.NhsJob;
import com.jobseekercopilot.generated.nhsjobsgateway.model.NhsJobsSearchRequest;
import com.jobseekercopilot.generated.nhsjobsgateway.model.NhsJobsSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component @Order(0)
public class NhsJobsProviderAdapter implements JobProviderAdapter {
    private static final Pattern POSTCODE = Pattern.compile("([A-Z]{1,2}[0-9][A-Z0-9]?\\s*[0-9][A-Z]{2})$", Pattern.CASE_INSENSITIVE);
    private final NhsJobsApi api; private final boolean enabled; private final int resultsPerPage;
    public NhsJobsProviderAdapter(NhsJobsApi api,
            @Value("${providers.nhs-jobs.enabled:${NHS_JOBS_ENABLED:true}}") boolean enabled,
            @Value("${providers.nhs-jobs.results-per-page:${NHS_JOBS_RESULTS_PER_PAGE:50}}") int resultsPerPage) {
        this.api=api; this.enabled=enabled; this.resultsPerPage=resultsPerPage;
    }
    @Override public String provider(){return "NHS_JOBS";} @Override public boolean isEnabled(){return enabled;}
    @Override public List<Job> search(String userId, JobSearchCriteria criteria) {
        NhsJobsSearchRequest request = new NhsJobsSearchRequest(); request.setTargetRole(criteria.getTargetRole()); request.setLocation(criteria.getLocation());
        request.setDistanceMiles(criteria.getDistanceMiles()); request.setCountryCode(nhsCountryCode(criteria.getCountryCode())); request.setWorkingPatterns(workingPatterns(criteria.getEmploymentTypes()));
        request.setSalaryMinimum(criteria.getSalaryMin()); request.setSalaryMaximum(criteria.getSalaryMax()); request.setPage(1); request.setResultsPerPage(resultsPerPage);
        NhsJobsSearchResponse response = api.search(request); return response == null || response.getJobs() == null ? List.of() : response.getJobs().stream().map(this::map).toList();
    }
    private Job map(NhsJob source) {
        Job job = new Job(); job.setId(source.getExternalJobId()); job.setCanonicalJobId(source.getExternalJobId()); job.setProvider(provider()); job.setPrimarySource(provider());
        job.setExternalJobId(source.getReference()); job.setTitle(source.getTitle()); job.setJobTitle(source.getTitle()); job.setCompany(source.getEmployer()); job.setCompanyName(source.getEmployer());
        job.setHiringOrganisationName(source.getEmployer()); job.setSpecialistType(JobSpecialistType.NHS); job.setContractType(source.getContractType()); job.setCategory("NHS");
        List<CanonicalLocation> locations = source.getLocations() == null ? List.of() : source.getLocations().stream().map(this::location).toList();
        job.setLocations(locations); if (!locations.isEmpty()) { job.setCanonicalLocation(locations.get(0)); job.setLocation(locations.get(0).getDisplayName()); }
        if (source.getSalaryMinimum() != null || source.getSalaryMaximum() != null) {
            JobSalary salary = new JobSalary(); salary.setRawMinimum(source.getSalaryMinimum()); salary.setRawMaximum(source.getSalaryMaximum()); salary.setMinimum(source.getSalaryMinimum()); salary.setMaximum(source.getSalaryMaximum());
            salary.setMin(integer(source.getSalaryMinimum())); salary.setMax(integer(source.getSalaryMaximum())); salary.setCurrency("GBP"); salary.setRawCurrency(source.getSalaryCurrency()); salary.setCurrencyCode(source.getSalaryCurrency());
            salary.setPeriod(source.getSalaryPeriod()); salary.setRawPeriod(source.getSalaryPeriod()); salary.setPeriodCode(period(source.getSalaryPeriod())); salary.setSourceProvider(provider()); salary.setNormalisationStatus(CanonicalValueStatus.RAW_ONLY); job.setSalary(salary);
        }
        job.setPostedDate(source.getPostedAt()); job.setPostedAt(source.getPostedAt()); job.setPostedAtUtc(CanonicalJobMappingSupport.parseOffsetDateTime(source.getPostedAt()));
        job.setExpiresAt(source.getClosingDate()); job.setApplicationDeadlineAtUtc(endOfDay(source.getClosingDate())); job.setDescription(source.getDescription());
        job.setDescriptionCompleteness(source.getDescription()==null||source.getDescription().isBlank()?JobDescriptionCompleteness.UNKNOWN:JobDescriptionCompleteness.PREVIEW);
        String url=CanonicalUrlPolicy.safeHttpUrl(source.getSourceUrl()); job.setUrl(url); job.setSourceUrl(url);
        JobSourceReference reference=new JobSourceReference(); reference.setProvider(provider()); reference.setExternalJobId(source.getReference()); reference.setRawPublisher("NHS Jobs"); reference.setPublisher("NHS Jobs");
        reference.setSourceType(JobSourceType.OFFICIAL_PROVIDER); reference.setListingUrl(url); reference.setApplyUrl(url); reference.setDirectApply(true); reference.setProviderPostedAtRaw(source.getPostedAt()); reference.setProviderPostedAtUtc(job.getPostedAtUtc()); job.setSources(List.of(reference));
        job.setFieldProvenance(List.of(CanonicalJobMappingSupport.rawField(provider(),source.getExternalJobId(),"title",source.getTitle()), CanonicalJobMappingSupport.rawField(provider(),source.getExternalJobId(),"description",source.getDescription()), CanonicalJobMappingSupport.rawField(provider(),source.getExternalJobId(),"locations",source.getLocations())));
        return job;
    }
    private CanonicalLocation location(String display) { CanonicalLocation result=new CanonicalLocation(); result.setRawDisplayName(display); result.setDisplayName(display); result.setSourceProvider(provider()); result.setNormalisationStatus(display==null?CanonicalValueStatus.NOT_PROVIDED:CanonicalValueStatus.RAW_ONLY); if(display!=null){Matcher m=POSTCODE.matcher(display.trim().toUpperCase(Locale.ROOT)); if(m.find()) result.setPostcode(m.group(1).replaceAll("\\s+"," "));} return result; }
    private String nhsCountryCode(String code){ if(code==null)return null; return switch(code.toUpperCase(Locale.ROOT)){case "GB-WLS","WLS"->"GB-WLS";case "GB-SCT","SCT"->"GB-SCT";case "GB-ENG","ENG","GB"->"GB-ENG";default->code;}; }
    private List<String> workingPatterns(List<String> employmentTypes) {
        if (employmentTypes == null) return List.of();
        return employmentTypes.stream().map(value -> switch (value.toUpperCase(Locale.ROOT)) {
            case "FULL_TIME" -> "Full time";
            case "PART_TIME" -> "Part time";
            default -> null;
        }).filter(java.util.Objects::nonNull).distinct().toList();
    }
    private SalaryPeriodCode period(String value){ if(value==null)return SalaryPeriodCode.UNKNOWN; try{return SalaryPeriodCode.valueOf(value.toUpperCase(Locale.ROOT));}catch(RuntimeException ignored){return SalaryPeriodCode.UNKNOWN;} }
    private Integer integer(BigDecimal value){return value==null?null:value.intValue();}
    private java.time.OffsetDateTime endOfDay(String value){try{return LocalDate.parse(value.substring(0,10)).atTime(23,59,59).atOffset(ZoneOffset.UTC);}catch(RuntimeException ignored){return null;}}
}
