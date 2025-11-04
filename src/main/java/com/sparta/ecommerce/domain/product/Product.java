package com.sparta.ecommerce.domain.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private String productId;
    private String name;
    private double price;
    private int stock;
    private String category;
    private String description;
}

/*
// 상품 정보
Table products {
id varchar [pk]
name varchar [not null]
description text
price decimal(10,2) [not null]
stock int [default: 0, not null]
category varchar
created_at timestamp [default: `now()`]
updated_at timestamp [default: `now()`]

indexes {
    (category)
            (created_at)
}
}*/
