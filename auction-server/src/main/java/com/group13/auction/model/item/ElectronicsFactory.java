package com.group13.auction.model.item;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.iservice.IRatingService;
import java.util.List;

/** Factory cụ thể để tạo các đối tượng Electronics. */
public class ElectronicsFactory extends ItemFactory {

    public ElectronicsFactory(IRatingService ratingService) { super(ratingService); }

    /**
     * @param args [0] brand (String), [1] warrantyMonths (int), [2] condition (String),
     *             [3] imageUrls (List&lt;String&gt;, optional)
     */
    @Override
    @SuppressWarnings("unchecked")
    protected Item createProduct(String name, String description, long startingPrice,
                                 NormalUser seller, Object... args) {
        String brand       = (String) args[0];
        int warrantyMonths = (int)    args[1];
        String condition   = (String) args[2];
        List<String> imgs  = (args.length > 3 && args[3] instanceof List)
                ? (List<String>) args[3] : List.of();
        return Electronics.create(name, description, startingPrice, seller,
                brand, warrantyMonths, condition, imgs);
    }
}