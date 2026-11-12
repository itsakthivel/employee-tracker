package com.example.tracker.reference;

import com.example.tracker.common.BaseEntity;
import com.example.tracker.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "department")
public class Department extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "head_employee_id")
  private Employee head;

  protected Department() {}

  public Department(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Employee getHead() {
    return head;
  }

  public void setHead(Employee head) {
    this.head = head;
  }
}
