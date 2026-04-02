package com.jimbro.dto;

public class ReviseDietRequest {
    private Object currentDiet;
    private String changeRequest;
    private Object userProfile;
    private String dietPreference;

    public ReviseDietRequest() {}

    public Object getCurrentDiet() { return currentDiet; }
    public void setCurrentDiet(Object currentDiet) { this.currentDiet = currentDiet; }

    public String getChangeRequest() { return changeRequest; }
    public void setChangeRequest(String changeRequest) { this.changeRequest = changeRequest; }

    public Object getUserProfile() { return userProfile; }
    public void setUserProfile(Object userProfile) { this.userProfile = userProfile; }

    public String getDietPreference() { return dietPreference; }
    public void setDietPreference(String dietPreference) { this.dietPreference = dietPreference; }
}
