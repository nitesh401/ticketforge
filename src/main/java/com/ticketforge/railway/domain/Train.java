package com.ticketforge.railway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trains")
public class Train {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "train_no")
    private String trainNo;
    private String name;
    @Column(name = "source_station")
    private String sourceStation;
    @Column(name = "destination_station")
    private String destinationStation;

    protected Train() {
    }

    public Long getId() { return id; }
    public String getTrainNo() { return trainNo; }
    public String getName() { return name; }
    public String getSourceStation() { return sourceStation; }
    public String getDestinationStation() { return destinationStation; }
}
