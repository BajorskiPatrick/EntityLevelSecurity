package com.els.demo.service;

import com.els.annotation.Secure;
import com.els.demo.domain.Product;
import com.els.demo.repository.ProductRepository;
import com.els.domain.Action;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Secure(entity = Product.class, action = Action.SELECT)
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        // This query will be intercepted by SecurityAspect
        // The aspect will enable 'elsFilter' with allowed IDs
        return productRepository.findAll();
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }
}
