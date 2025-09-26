package com.example.reloader.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "external_location")
public class ExternalLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private ExternalEnvironment environment;

    @Column(nullable = false)
    private String label;

    @Column(name = "db_connection_name", nullable = false)
    private String dbConnectionName;

    @Column(length = 2000)
    private String details;

    @Column(length = 100)
    private String site;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public ExternalEnvironment getEnvironment() { return environment; }
    public void setEnvironment(ExternalEnvironment environment) { this.environment = environment; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDbConnectionName() { return dbConnectionName; }
    public void setDbConnectionName(String dbConnectionName) { this.dbConnectionName = dbConnectionName; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
}
