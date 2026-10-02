package com.catalyst.infrastructure;

import com.catalyst.decision.Product;
import com.catalyst.decision.ProductCatalog;
import com.catalyst.infrastructure.persistence.Repositories.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaProductCatalog implements ProductCatalog {
    private final ProductRepository repo;

    public JpaProductCatalog(ProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Product> all() {
        return repo.findAll().stream().map(p -> new Product(p.getId(), p.getName(), p.getCategory(), p.getMinAmount(),
                p.getMaxAmount(), p.getIndicativeRate(), p.getRiskLevel(), p.getDescription())).toList();
    }
}
