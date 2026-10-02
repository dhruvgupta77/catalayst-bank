package com.catalyst.decision;

import java.util.List;
import java.util.Optional;

/**
 * Port for the product catalogue; replaceable by a real product-factory API.
 */
public interface ProductCatalog {
    List<Product> all();

    default Optional<Product> byId(String id) {
        return all().stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
