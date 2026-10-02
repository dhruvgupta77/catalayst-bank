package com.catalyst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class ProductEntity {
    @Id
    private String id;
    private String name;
    private String category;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal indicativeRate;
    private int riskLevel;
    private String description;
}
