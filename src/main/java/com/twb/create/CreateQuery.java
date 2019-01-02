package com.twb.create;

public class CreateQuery {

    private final String entityName;

    private final char firstLetterAlias;

    public CreateQuery(String entityName) {
        this.entityName = entityName;
        this.firstLetterAlias = entityName.toLowerCase().charAt(0);
    }

    public String createFindByIdQuery() {
        return "\"SELECT " + firstLetterAlias + " \" +\n" +
                "\"FROM " + entityName + " " + firstLetterAlias + " \" +\n" +
                "\"WHERE (" + firstLetterAlias + ".deleted IS NULL OR " + firstLetterAlias + ".deleted = FALSE) \" +\n" +
                "\"AND " + firstLetterAlias + ".id = :id\"";
    }

    public String createFindAllQuery() {
        return "\"SELECT " + firstLetterAlias + " \" +\n" +
                "\"FROM " + entityName + " " + firstLetterAlias + " \" +\n" +
                "\"WHERE (" + firstLetterAlias + ".deleted IS NULL OR " + firstLetterAlias + ".deleted = FALSE)\"";
    }

    public String createFileDeletedByIdQuery() {
        return "\"SELECT " + firstLetterAlias + " \" +\n" +
                "\"FROM " + entityName + " " + firstLetterAlias + " \" +\n" +
                "\"WHERE " + firstLetterAlias + ".deleted = TRUE \" +\n" +
                "\"AND " + firstLetterAlias + ".id = :id\"";
    }

    public String createFindAllDeletedQuery() {
        return "\"SELECT " + firstLetterAlias + " \" +\n" +
                "\"FROM " + entityName + " " + firstLetterAlias + " \" +\n" +
                "\"WHERE " + firstLetterAlias + ".deleted = TRUE\"";
    }
}
