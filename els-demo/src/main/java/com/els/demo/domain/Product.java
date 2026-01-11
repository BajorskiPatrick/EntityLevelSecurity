package com.els.demo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "products")
@Getter
@Setter
// ELS Requirement: Define the security filter
@FilterDef(name = "elsFilter", parameters = @ParamDef(name = "ids", type = Long.class))
@Filter(name = "elsFilter", condition = "id IN (:ids)")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String category;
    private Double price;
}
