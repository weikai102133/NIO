package rpcCommon;

import java.io.Serializable;

public class Product implements Serializable {
    private final static long serialVersionUID = 12L;
    private Integer id;
    private String name;

    public Product(Integer id,String name){
        this.id= id;
        this.name = name;
    }
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString(){
        return "Product{"+ "id=" + id + ", name=  " + name + "}";
    }
}
