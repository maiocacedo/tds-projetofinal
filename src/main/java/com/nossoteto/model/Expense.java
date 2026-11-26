package com.nossoteto.model;

import com.nossoteto.model.enums.SplitType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    private String id;

    @Column(name = "house_id", nullable = false)
    private String houseId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "split_type", nullable = false)
    private SplitType splitType = SplitType.EQUAL;

    @Column(name = "paid_by_id")
    private String paidById;

    @Column(name = "created_by_id")
    private String createdById;

    @Column(name = "recurring_day")
    private Integer recurringDay;

    // Initialized on purpose: Hibernate would otherwise insert NULL and ignore the column DEFAULT.
    @Column(name = "recurrence_active", nullable = false)
    private Boolean recurrenceActive = false;

    @Column(name = "recurrence_source_id")
    private String recurrenceSourceId;

    @Column(name = "recurrence_month")
    private String recurrenceMonth;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public SplitType getSplitType() { return splitType; }
    public void setSplitType(SplitType splitType) { this.splitType = splitType; }

    public String getPaidById() { return paidById; }
    public void setPaidById(String paidById) { this.paidById = paidById; }

    public String getCreatedById() { return createdById; }
    public void setCreatedById(String createdById) { this.createdById = createdById; }

    public Integer getRecurringDay() { return recurringDay; }
    public void setRecurringDay(Integer recurringDay) { this.recurringDay = recurringDay; }

    public Boolean getRecurrenceActive() { return recurrenceActive; }
    public void setRecurrenceActive(Boolean recurrenceActive) { this.recurrenceActive = recurrenceActive; }

    public String getRecurrenceSourceId() { return recurrenceSourceId; }
    public void setRecurrenceSourceId(String recurrenceSourceId) { this.recurrenceSourceId = recurrenceSourceId; }

    public String getRecurrenceMonth() { return recurrenceMonth; }
    public void setRecurrenceMonth(String recurrenceMonth) { this.recurrenceMonth = recurrenceMonth; }
}
