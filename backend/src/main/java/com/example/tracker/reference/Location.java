package com.example.tracker.reference;

import com.example.tracker.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "location")
public class Location extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String name;

  protected Location() {}

  public Location(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
