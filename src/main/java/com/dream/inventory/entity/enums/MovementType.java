package com.dream.inventory.entity.enums;

public enum MovementType {
    PURCHASE_IN, SALE_OUT, SALE_RETURN, PURCHASE_RETURN,
    TRANSFER, ADJUST, OTHER_IN, OTHER_OUT;

    public String docPrefix() {
        return switch (this) {
            case PURCHASE_IN -> "PI";
            case SALE_OUT -> "SO";
            case SALE_RETURN -> "SR";
            case PURCHASE_RETURN -> "PR";
            case TRANSFER -> "TF";
            case ADJUST -> "AJ";
            case OTHER_IN -> "OI";
            case OTHER_OUT -> "OO";
        };
    }
}
