package com.jimbro.dto;

public class GeneratePlanResponse {
    private String workoutPlan;
    private String dietPlan;

    public GeneratePlanResponse() {}

    public GeneratePlanResponse(String workoutPlan, String dietPlan) {
        this.workoutPlan = workoutPlan;
        this.dietPlan = dietPlan;
    }

    public String getWorkoutPlan() { return workoutPlan; }
    public void setWorkoutPlan(String workoutPlan) { this.workoutPlan = workoutPlan; }

    public String getDietPlan() { return dietPlan; }
    public void setDietPlan(String dietPlan) { this.dietPlan = dietPlan; }
}
