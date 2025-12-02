package com.els.parser;

import com.els.logger.Logger;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.update.Update;

import java.util.Collections;

public class SqlRewriter {

    private final Logger logger = Logger.getInstance();

    public String addAclToSql(String originalSql, String userId) {
        try {
            Statement statement = CCJSqlParserUtil.parse(originalSql);

            // SELECT - dodajemy JOIN do tabeli ACL
            if (statement instanceof Select) {
                return handleSelect((Select) statement, userId);
            }

            // UPDATE - filtrujemy które rekordy można modyfikować
            if (statement instanceof Update) {
                return handleUpdate((Update) statement, userId);
            }

            // DELETE - filtrujemy które rekordy można usuwać
            if (statement instanceof Delete) {
                return handleDelete((Delete) statement, userId);
            }

            // INSERT, DDL, etc. - bez zmian
            logger.log("Statement type not handled for ACL: " + statement.getClass().getSimpleName());

        } catch (JSQLParserException e) {
            logger.logException(e);
        }
        return originalSql;
    }

    /**
     * Obsługuje SELECT - dodaje JOIN do els_acl_table i filtruje wyniki.
     */
    private String handleSelect(Select select, String userId) {
        try {
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

            // 1. Pobieramy główną tabelę z zapytania (np. "books")
            if (!(plainSelect.getFromItem() instanceof Table)) {
                return select.toString(); // Nie obsługujemy podzapytań w FROM
            }
            Table mainTable = (Table) plainSelect.getFromItem();
            String mainTableName = mainTable.getName();

            if ("els_acl_table".equalsIgnoreCase(mainTableName)) {
                return select.toString();
            }

            // Ustalamy alias (jeśli jest, to używamy, jeśli nie - nazwa tabeli)
            Alias mainTableAlias = mainTable.getAlias();
            String effectiveMainTableRef = (mainTableAlias != null) ? mainTableAlias.getName() : mainTableName;

            // 2. Tworzymy JOIN do tabeli ACL
            Table aclTable = new Table("els_acl_table");
            Alias aclAlias = new Alias("acl_security", false);
            aclTable.setAlias(aclAlias);

            // Warunek złączenia: acl.row_id = main.id
            EqualsTo joinConditionId = new EqualsTo();
            joinConditionId.setLeftExpression(new Column(aclAlias.getName() + ".row_id"));
            joinConditionId.setRightExpression(new Column(effectiveMainTableRef + ".id"));

            // Warunek złączenia: acl.table_name = 'nazwa_tabeli'
            EqualsTo joinConditionTable = new EqualsTo();
            joinConditionTable.setLeftExpression(new Column(aclAlias.getName() + ".table_name"));
            joinConditionTable.setRightExpression(new StringValue(mainTableName));

            AndExpression joinOn = new AndExpression(joinConditionId, joinConditionTable);

            Join join = new Join();
            join.setRightItem(aclTable);
            join.setOnExpression(joinOn);
            join.setInner(true);

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
        } catch (Exception e) {
            logger.logException(e);
            return select.toString();
        }
    }

    /**
     * Obsługuje UPDATE - dodaje WHERE z subquery do els_acl_table.
     * Przykład:
     *   UPDATE books SET title = 'New' WHERE category = 'fiction'
     * Staje się:
     *   UPDATE books SET title = 'New' WHERE category = 'fiction'
     *   AND id IN (SELECT row_id FROM els_acl_table WHERE user_id = 'alice' AND table_name = 'books')
     */
    private String handleUpdate(Update update, String userId) {
        try {
            Table table = update.getTable();
            String tableName = table.getName();

            if ("els_acl_table".equalsIgnoreCase(tableName)) {
                return update.toString();
            }

            // Tworzymy subquery: SELECT row_id FROM els_acl_table WHERE user_id = ? AND table_name = ?
            PlainSelect subSelect = new PlainSelect();
            subSelect.setSelectItems(Collections.singletonList(new net.sf.jsqlparser.statement.select.SelectExpressionItem(new Column("row_id"))));
            subSelect.setFromItem(new Table("els_acl_table"));

            EqualsTo userIdCondition = new EqualsTo();
            userIdCondition.setLeftExpression(new Column("user_id"));
            userIdCondition.setRightExpression(new StringValue(userId));

            EqualsTo tableNameCondition = new EqualsTo();
            tableNameCondition.setLeftExpression(new Column("table_name"));
            tableNameCondition.setRightExpression(new StringValue(tableName));

            subSelect.setWhere(new AndExpression(userIdCondition, tableNameCondition));

            // Tworzymy IN clause: id IN (subquery)
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(new Column("id"));
            inExpression.setRightExpression(new SubSelect().withSelectBody(subSelect));

            // Dodajemy do WHERE
            if (update.getWhere() == null) {
                update.setWhere(inExpression);
            } else {
                update.setWhere(new AndExpression(update.getWhere(), inExpression));
            }

            return update.toString();
        } catch (Exception e) {
            logger.logException(e);
            return update.toString();
        }
    }

    /**
     * Obsługuje DELETE - dodaje WHERE z subquery do els_acl_table.
     * Przykład:
     *   DELETE FROM books WHERE year < 2000
     * Staje się:
     *   DELETE FROM books WHERE year < 2000
     *   AND id IN (SELECT row_id FROM els_acl_table WHERE user_id = 'alice' AND table_name = 'books')
     */
    private String handleDelete(Delete delete, String userId) {
        try {
            Table table = delete.getTable();
            String tableName = table.getName();

            if ("els_acl_table".equalsIgnoreCase(tableName)) {
                return delete.toString();
            }

            // Tworzymy subquery: SELECT row_id FROM els_acl_table WHERE user_id = ? AND table_name = ?
            PlainSelect subSelect = new PlainSelect();
            subSelect.setSelectItems(Collections.singletonList(new net.sf.jsqlparser.statement.select.SelectExpressionItem(new Column("row_id"))));
            subSelect.setFromItem(new Table("els_acl_table"));

            EqualsTo userIdCondition = new EqualsTo();
            userIdCondition.setLeftExpression(new Column("user_id"));
            userIdCondition.setRightExpression(new StringValue(userId));

            EqualsTo tableNameCondition = new EqualsTo();
            tableNameCondition.setLeftExpression(new Column("table_name"));
            tableNameCondition.setRightExpression(new StringValue(tableName));

            subSelect.setWhere(new AndExpression(userIdCondition, tableNameCondition));

            // Tworzymy IN clause: id IN (subquery)
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(new Column("id"));
            inExpression.setRightExpression(new SubSelect().withSelectBody(subSelect));

            // Dodajemy do WHERE
            if (delete.getWhere() == null) {
                delete.setWhere(inExpression);
            } else {
                delete.setWhere(new AndExpression(delete.getWhere(), inExpression));
            }

            return delete.toString();
        } catch (Exception e) {
            logger.logException(e);
            return delete.toString();
        }
    }
}