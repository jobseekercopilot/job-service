package com.jobseekercopilot.jobservice.model.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ApprenticeshipDetails {
    private String courseTitle; private Integer courseLevel; private Integer courseLarsCode; private String courseRoute;
    private String apprenticeshipLevel; private String trainingProvider; private String startDate; private String duration;
    private BigDecimal hoursPerWeek; private Integer numberOfPositions; private String wageType; private String wageAdditionalInformation;
    private String workingWeekDescription; private Boolean nationalVacancy; private String nationalVacancyDetails;
    private List<String> qualifications = new ArrayList<>(); private String thingsToConsider; private String companyBenefitsInformation;
    public String getCourseTitle(){return courseTitle;} public void setCourseTitle(String v){courseTitle=v;}
    public Integer getCourseLevel(){return courseLevel;} public void setCourseLevel(Integer v){courseLevel=v;}
    public Integer getCourseLarsCode(){return courseLarsCode;} public void setCourseLarsCode(Integer v){courseLarsCode=v;}
    public String getCourseRoute(){return courseRoute;} public void setCourseRoute(String v){courseRoute=v;}
    public String getApprenticeshipLevel(){return apprenticeshipLevel;} public void setApprenticeshipLevel(String v){apprenticeshipLevel=v;}
    public String getTrainingProvider(){return trainingProvider;} public void setTrainingProvider(String v){trainingProvider=v;}
    public String getStartDate(){return startDate;} public void setStartDate(String v){startDate=v;}
    public String getDuration(){return duration;} public void setDuration(String v){duration=v;}
    public BigDecimal getHoursPerWeek(){return hoursPerWeek;} public void setHoursPerWeek(BigDecimal v){hoursPerWeek=v;}
    public Integer getNumberOfPositions(){return numberOfPositions;} public void setNumberOfPositions(Integer v){numberOfPositions=v;}
    public String getWageType(){return wageType;} public void setWageType(String v){wageType=v;}
    public String getWageAdditionalInformation(){return wageAdditionalInformation;} public void setWageAdditionalInformation(String v){wageAdditionalInformation=v;}
    public String getWorkingWeekDescription(){return workingWeekDescription;} public void setWorkingWeekDescription(String v){workingWeekDescription=v;}
    public Boolean getNationalVacancy(){return nationalVacancy;} public void setNationalVacancy(Boolean v){nationalVacancy=v;}
    public String getNationalVacancyDetails(){return nationalVacancyDetails;} public void setNationalVacancyDetails(String v){nationalVacancyDetails=v;}
    public List<String> getQualifications(){return qualifications;} public void setQualifications(List<String> v){qualifications=v==null?new ArrayList<>():v;}
    public String getThingsToConsider(){return thingsToConsider;} public void setThingsToConsider(String v){thingsToConsider=v;}
    public String getCompanyBenefitsInformation(){return companyBenefitsInformation;} public void setCompanyBenefitsInformation(String v){companyBenefitsInformation=v;}
}
