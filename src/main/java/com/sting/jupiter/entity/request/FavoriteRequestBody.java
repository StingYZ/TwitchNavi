package com.sting.jupiter.entity.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sting.jupiter.entity.db.Item;

public class FavoriteRequestBody {
    private final Item item;

    @JsonCreator
    public FavoriteRequestBody(@JsonProperty("favorite") Item item){
        this.item=item;
    }
    public Item getItem(){
        return item;
    }
}
