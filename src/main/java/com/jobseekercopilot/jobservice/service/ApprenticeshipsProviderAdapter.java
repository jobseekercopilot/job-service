package com.jobseekercopilot.jobservice.service;

import com.jobseekercopilot.generated.apprenticeshipsgateway.api.ApprenticeshipsApi;
import com.jobseekercopilot.generated.apprenticeshipsgateway.model.ApprenticeshipAddress;
import com.jobseekercopilot.generated.apprenticeshipsgateway.model.ApprenticeshipVacancy;
import com.jobseekercopilot.generated.apprenticeshipsgateway.model.ApprenticeshipsSearchRequest;
import com.jobseekercopilot.generated.apprenticeshipsgateway.model.ApprenticeshipsSearchResponse;
import com.jobseekercopilot.jobservice.model.dto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component @Order(0)
public class ApprenticeshipsProviderAdapter implements JobProviderAdapter {
    private final ApprenticeshipsApi api; private final boolean enabled; private final int resultsPerPage; private final DistanceCalculationService distance;
    public ApprenticeshipsProviderAdapter(ApprenticeshipsApi api, DistanceCalculationService distance,
            @Value("${providers.apprenticeships.enabled:${APPRENTICESHIPS_ENABLED:true}}") boolean enabled,
            @Value("${providers.apprenticeships.results-per-page:${APPRENTICESHIPS_RESULTS_PER_PAGE:50}}") int resultsPerPage) {
        this.api=api; this.distance=distance; this.enabled=enabled; this.resultsPerPage=resultsPerPage;
    }
    @Override public String provider(){return "APPRENTICESHIPS";} @Override public boolean isEnabled(){return enabled;}
    @Override public List<Job> search(String userId, JobSearchCriteria criteria){
        ApprenticeshipsSearchRequest request=new ApprenticeshipsSearchRequest(); request.setTargetRole(criteria.getTargetRole()); request.setLocation(criteria.getLocation()); request.setDistanceMiles(criteria.getDistanceMiles());
        request.setLatitude(decimal(criteria.getHomeLatitude())); request.setLongitude(decimal(criteria.getHomeLongitude())); request.setPage(1); request.setResultsPerPage(resultsPerPage);
        ApprenticeshipsSearchResponse response=api.search(request); return response==null||response.getJobs()==null?List.of():response.getJobs().stream().map(source->map(source,criteria)).toList();
    }
    private Job map(ApprenticeshipVacancy source, JobSearchCriteria criteria){
        Job job=new Job(); job.setId(source.getVacancyReference()); job.setCanonicalJobId(source.getVacancyReference()); job.setProvider(provider()); job.setPrimarySource(provider()); job.setExternalJobId(source.getVacancyReference());
        job.setTitle(source.getTitle()); job.setJobTitle(source.getTitle()); job.setCompany(source.getEmployerName()); job.setCompanyName(source.getEmployerName()); job.setHiringOrganisationName(source.getEmployerName());
        job.setSpecialistType(JobSpecialistType.APPRENTICESHIP); job.setContractType("Apprenticeship"); job.setContractTypeCode(ContractTypeCode.OTHER); job.setCategory(first(source.getCourseTitle(), source.getCourseRoute(), "Apprenticeship"));
        List<CanonicalLocation> locations=source.getAddresses()==null?List.of():source.getAddresses().stream().map(this::location).toList(); job.setLocations(locations);
        CanonicalLocation primary=locations.stream().min(Comparator.comparingDouble(value->miles(criteria,value))).orElse(null); job.setCanonicalLocation(primary); if(primary!=null)job.setLocation(primary.getDisplayName());
        if(source.getWageAmount()!=null){JobSalary salary=new JobSalary(); salary.setRawMinimum(source.getWageAmount()); salary.setMinimum(source.getWageAmount()); salary.setMin(source.getWageAmount().intValue()); salary.setRawCurrency("GBP"); salary.setCurrency("GBP"); salary.setCurrencyCode("GBP");
            String unit=wagePeriod(source.getWageUnit()); salary.setRawPeriod(source.getWageUnit()); salary.setPeriod(unit); salary.setPeriodCode(period(unit)); salary.setSourceProvider(provider()); salary.setNormalisationStatus(CanonicalValueStatus.RAW_ONLY); job.setSalary(salary);}
        job.setPostedDate(source.getPostedDate()); job.setPostedAt(source.getPostedDate()); job.setPostedAtUtc(CanonicalJobMappingSupport.parseOffsetDateTime(source.getPostedDate())); job.setExpiresAt(source.getClosingDate()); job.setApplicationDeadlineAtUtc(endOfDay(source.getClosingDate()));
        job.setDescription(first(source.getFullDescription(),source.getDescription())); job.setDescriptionCompleteness(source.getFullDescription()!=null?JobDescriptionCompleteness.FULL:JobDescriptionCompleteness.PREVIEW);
        String listing=CanonicalUrlPolicy.safeHttpUrl(source.getVacancyUrl()); String apply=CanonicalUrlPolicy.safeHttpUrl(source.getApplicationUrl()); job.setSourceUrl(listing); job.setUrl(first(apply,listing));
        JobSourceReference reference=new JobSourceReference(); reference.setProvider(provider()); reference.setExternalJobId(source.getVacancyReference()); reference.setRawPublisher("Find an apprenticeship"); reference.setPublisher("Find an apprenticeship"); reference.setSourceType(JobSourceType.OFFICIAL_PROVIDER); reference.setListingUrl(listing); reference.setApplyUrl(first(apply,listing)); reference.setDirectApply(true); reference.setProviderPostedAtRaw(source.getPostedDate()); reference.setProviderPostedAtUtc(job.getPostedAtUtc()); job.setSources(List.of(reference));
        ApprenticeshipDetails details=new ApprenticeshipDetails(); details.setCourseTitle(source.getCourseTitle()); details.setCourseLevel(source.getCourseLevel()); details.setCourseLarsCode(source.getCourseLarsCode()); details.setCourseRoute(source.getCourseRoute()); details.setApprenticeshipLevel(source.getApprenticeshipLevel()); details.setTrainingProvider(source.getProviderName());
        details.setStartDate(source.getStartDate()); details.setDuration(source.getExpectedDuration()); details.setHoursPerWeek(source.getHoursPerWeek()); details.setNumberOfPositions(source.getNumberOfPositions()); details.setWageType(source.getWageType()); details.setWageAdditionalInformation(source.getWageAdditionalInformation()); details.setWorkingWeekDescription(source.getWorkingWeekDescription()); details.setNationalVacancy(source.getNationalVacancy()); details.setNationalVacancyDetails(source.getNationalVacancyDetails()); details.setQualifications(source.getQualifications()); details.setThingsToConsider(source.getThingsToConsider()); details.setCompanyBenefitsInformation(source.getCompanyBenefitsInformation()); job.setApprenticeshipDetails(details);
        if(source.getSkills()!=null)job.setSkills(source.getSkills().stream().filter(Objects::nonNull).map(this::skill).toList());
        job.setFieldProvenance(List.of(CanonicalJobMappingSupport.rawField(provider(),source.getVacancyReference(),"title",source.getTitle()), CanonicalJobMappingSupport.rawField(provider(),source.getVacancyReference(),"courseTitle",source.getCourseTitle()), CanonicalJobMappingSupport.rawField(provider(),source.getVacancyReference(),"addresses",source.getAddresses())));
        return job;
    }
    private CanonicalLocation location(ApprenticeshipAddress source){CanonicalLocation value=new CanonicalLocation(); String display=java.util.stream.Stream.of(source.getAddressLine1(),source.getAddressLine2(),source.getAddressLine3(),source.getAddressLine4(),source.getPostcode()).filter(v->v!=null&&!v.isBlank()).distinct().collect(java.util.stream.Collectors.joining(", ")); value.setRawDisplayName(display); value.setDisplayName(display); value.setPostcode(source.getPostcode()); value.setLatitude(source.getLatitude()); value.setLongitude(source.getLongitude()); value.setSourceProvider(provider()); value.setNormalisationStatus(CanonicalValueStatus.RAW_ONLY); return value;}
    private double miles(JobSearchCriteria criteria, CanonicalLocation value){Double result=distance.calculateMiles(criteria.getHomeLatitude(),criteria.getHomeLongitude(),value.getLatitude()==null?null:value.getLatitude().doubleValue(),value.getLongitude()==null?null:value.getLongitude().doubleValue()); return result==null?Double.MAX_VALUE:result;}
    private JobSkill skill(String raw){JobSkill skill=new JobSkill();skill.setName(raw);skill.setRawName(raw);skill.setType(JobSkillType.UNKNOWN);skill.setSourceProvider(provider());skill.setNormalisationStatus(CanonicalValueStatus.RAW_ONLY);return skill;}
    private BigDecimal decimal(Double value){return value==null?null:BigDecimal.valueOf(value);} private String first(String...values){for(String value:values)if(value!=null&&!value.isBlank())return value;return null;}
    private String wagePeriod(String value){if(value==null)return null;String lower=value.toLowerCase(Locale.ROOT);if(lower.contains("annual")||lower.contains("year"))return "YEAR";if(lower.contains("month"))return "MONTH";if(lower.contains("week"))return "WEEK";if(lower.contains("day"))return "DAY";if(lower.contains("hour"))return "HOUR";return value;}
    private SalaryPeriodCode period(String value){if(value==null)return SalaryPeriodCode.UNKNOWN;try{return SalaryPeriodCode.valueOf(value);}catch(RuntimeException ignored){return SalaryPeriodCode.UNKNOWN;}}
    private java.time.OffsetDateTime endOfDay(String value){try{return LocalDate.parse(value.substring(0,10)).atTime(23,59,59).atOffset(ZoneOffset.UTC);}catch(RuntimeException ignored){return null;}}
}
