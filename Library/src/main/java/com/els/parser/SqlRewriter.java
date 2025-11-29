package com.els.parser;

import com.els.logger.Logger;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.Collections;

public class SqlRewriter {

    private final Logger logger = Logger.getInstance();

    public String addAclToSql(String originalSql, String userId) {
        try {
            Statement statement = CCJSqlParserUtil.parse(originalSql);

            // Dla uproszczenia obsługujemy tylko SELECT
            if (statement instanceof Select) {
                Select select = (Select) statement;
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

                // 1. Pobieramy główną tabelę z zapytania (np. "books")
                if (!(plainSelect.getFromItem() instanceof Table)) {
                    return originalSql; // Nie obsługujemy podzapytań w FROM w tym demo
                }
                Table mainTable = (Table) plainSelect.getFromItem();
                String mainTableName = mainTable.getName();

                if ("els_acl_table".equalsIgnoreCase(mainTableName)) {
                    return originalSql;
                }

                // Ustalamy alias (jeśli jest, to używamy, jeśli nie - nazwa tabeli)
                Alias mainTableAlias = mainTable.getAlias();
                String effectiveMainTableRef = (mainTableAlias != null) ? mainTableAlias.getName() : mainTableName;

                // 2. Tworzymy JOIN do tabeli ACL
                // JOIN els_acl_table acl ON acl.row_id = main.id AND acl.table_name = 'nazwa_tabeli'
                Table aclTable = new Table("els_acl_table");
                Alias aclAlias = new Alias("acl_security", false);
                aclTable.setAlias(aclAlias);

                // Warunek złączenia: acl.row_id = main.id
                EqualsTo joinConditionId = new EqualsTo();
                joinConditionId.setLeftExpression(new Column(aclAlias.getName() + ".row_id"));
                joinConditionId.setRightExpression(new Column(effectiveMainTableRef + ".id")); // Zakładamy kolumnę 'id'

                // Warunek złączenia: acl.table_name = 'nazwa_tabeli'
                EqualsTo joinConditionTable = new EqualsTo();
                joinConditionTable.setLeftExpression(new Column(aclAlias.getName() + ".table_name"));
                joinConditionTable.setRightExpression(new StringValue(mainTableName));

                AndExpression joinOn = new AndExpression(joinConditionId, joinConditionTable);

                Join join = new Join();
                join.setRightItem(aclTable);
                join.setOnExpression(joinOn);
                join.setInner(true); // Inner JOIN filtruje wyniki, do których nie mamy praw

                if (plainSelect.getJoins() == null) {
                    plainSelect.setJoins(Collections.singletonList(join));
                } else {
                    plainSelect.getJoins().add(join);
                }

                // 3. Dodajemy WHERE acl.user_id = 'current_user'
                EqualsTo userCheck = new EqualsTo();
                userCheck.setLeftExpression(new Column(aclAlias.getName() + ".user_id"));
                userCheck.setRightExpression(new StringValue(userId));

                if (plainSelect.getWhere() == null) {
                    plainSelect.setWhere(userCheck);
                } else {
                    plainSelect.setWhere(new AndExpression(plainSelect.getWhere(), userCheck));
                }

                return plainSelect.toString();
            }
        } catch (JSQLParserException e) {
            logger.logException(e);
        }
        return originalSql;
    }
}