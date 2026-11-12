package com.example.tracker.employee;

import com.example.tracker.common.BaseEntity;
import com.example.tracker.reference.Department;
import com.example.tracker.reference.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/**
 * An employee record. Field rules live in {@code EmployeeValidator}; who may see which field is
 * decided only by {@code EmployeeViewPolicy}.
 */
@Entity
@Table(name = "employee")
public class Employee extends BaseEntity {

  @Column(name = "employee_number", nullable = false, length = 20)
  private String employeeNumber;

  @Column(name = "full_name", nullable = false, length = 150)
  private String fullName;

  @Column(name = "work_email", nullable = false, length = 254)
  private String workEmail;

  @Column(length = 16)
  private String phone;

  @Column(name = "job_title", nullable = false, length = 100)
  private String jobTitle;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "department_id", nullable = false)
  private Department department;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id")
  private Location location;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "manager_id")
  private Employee manager;

  @Enumerated(EnumType.STRING)
  @Column(name = "employment_type", nullable = false, length = 20)
  private EmploymentType employmentType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EmploymentStatus status;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "home_address", length = 500)
  private String homeAddress;

  @Column(name = "emergency_contact_name", length = 150)
  private String emergencyContactName;

  @Column(name = "emergency_contact_relationship", length = 50)
  private String emergencyContactRelationship;

  @Column(name = "emergency_contact_phone", length = 16)
  private String emergencyContactPhone;

  @Column(length = 2000)
  private String notes;

  @Version private long version;

  public boolean isPreHire() {
    return startDate != null && startDate.isAfter(LocalDate.now());
  }

  public boolean isTerminated() {
    return status == EmploymentStatus.TERMINATED;
  }

  public String getEmployeeNumber() {
    return employeeNumber;
  }

  public void setEmployeeNumber(String employeeNumber) {
    this.employeeNumber = employeeNumber;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getWorkEmail() {
    return workEmail;
  }

  public void setWorkEmail(String workEmail) {
    this.workEmail = workEmail;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getJobTitle() {
    return jobTitle;
  }

  public void setJobTitle(String jobTitle) {
    this.jobTitle = jobTitle;
  }

  public Department getDepartment() {
    return department;
  }

  public void setDepartment(Department department) {
    this.department = department;
  }

  public Location getLocation() {
    return location;
  }

  public void setLocation(Location location) {
    this.location = location;
  }

  public Employee getManager() {
    return manager;
  }

  public void setManager(Employee manager) {
    this.manager = manager;
  }

  public EmploymentType getEmploymentType() {
    return employmentType;
  }

  public void setEmploymentType(EmploymentType employmentType) {
    this.employmentType = employmentType;
  }

  public EmploymentStatus getStatus() {
    return status;
  }

  public void setStatus(EmploymentStatus status) {
    this.status = status;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public String getHomeAddress() {
    return homeAddress;
  }

  public void setHomeAddress(String homeAddress) {
    this.homeAddress = homeAddress;
  }

  public String getEmergencyContactName() {
    return emergencyContactName;
  }

  public void setEmergencyContactName(String emergencyContactName) {
    this.emergencyContactName = emergencyContactName;
  }

  public String getEmergencyContactRelationship() {
    return emergencyContactRelationship;
  }

  public void setEmergencyContactRelationship(String emergencyContactRelationship) {
    this.emergencyContactRelationship = emergencyContactRelationship;
  }

  public String getEmergencyContactPhone() {
    return emergencyContactPhone;
  }

  public void setEmergencyContactPhone(String emergencyContactPhone) {
    this.emergencyContactPhone = emergencyContactPhone;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public long getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return "Employee[id=" + getId() + ", number=" + employeeNumber + "]";
  }
}
