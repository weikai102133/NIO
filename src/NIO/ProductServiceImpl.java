package NIO;

import rpcCommon.IProductService;
import rpcCommon.Product;

public class ProductServiceImpl implements IProductService {
    @Override
    public Product findProductById(Integer id) {
        return new Product(id,"cola");
    }
}
