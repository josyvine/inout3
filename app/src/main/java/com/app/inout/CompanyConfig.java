package com.inout.app.models;

import com.google.firebase.firestore.IgnoreExtraProperties;

/**
 * Model class representing an Office Location / Company Configuration.
 * Stored in Firestore under 'locations' collection.
 */
@IgnoreExtraProperties
public class CompanyConfig {

    private String id;
    private String name;        // e.g., "Headquarters", "Branch A"
    private double latitude;
    private double longitude;
    private float radius;       // Allowed radius in meters (default 100)

    public CompanyConfig() {
        // Default constructor required for Firestore
        this.radius = 100.0f; // Default safety radius
    }

    public CompanyConfig(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = 100.0f;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }
}