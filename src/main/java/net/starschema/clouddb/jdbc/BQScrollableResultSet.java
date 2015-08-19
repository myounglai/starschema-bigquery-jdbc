/**
 * Copyright (c) 2015, STARSCHEMA LTD.
 * All rights reserved.

 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This class implements the java.sql.Resultset interface
 */
package net.starschema.clouddb.jdbc;

import java.math.BigInteger;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.math.BigDecimal;

import com.google.api.services.bigquery.model.GetQueryResultsResponse;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableCell;
import com.google.api.client.util.Data;

/**
 * This class implements the java.sql.ResultSet interface its superclass is
 * ScrollableResultset
 *
 * @author Attila Horváth
 */
public class BQScrollableResultSet extends ScrollableResultset<Object> implements
        java.sql.ResultSet {

    /**
     * to set the maxFieldSize
     */
    private int maxFieldSize = 0;

    /**
     * This Reference is for storing the GetQueryResultsResponse got from
     * bigquery
     */
    private GetQueryResultsResponse Result = null;
    /**
     * This Reference is for storing the Reference for the Statement which
     * created this Resultset
     */
    private BQStatement Statementreference = null;

    public BQScrollableResultSet(GetQueryResultsResponse bigQueryGetQueryResultResponse,
                                 BQPreparedStatement bqPreparedStatement) {
        logger.debug("Created Scrollable resultset TYPE_SCROLL_INSENSITIVE");
        this.Result = bigQueryGetQueryResultResponse;
        BigInteger maxrow;
        try {
            maxrow = BigInteger.valueOf(bqPreparedStatement.getMaxRows());
            this.Result.setTotalRows(maxrow);
        } catch (SQLException e) {
            // Should not happen.
        }

        try {
            maxFieldSize = bqPreparedStatement.getMaxFieldSize();
        } catch (SQLException e) {
            // Should not happen.
        }

        if (this.Result.getRows() == null) {
            this.RowsofResult = null;
        } else {
            this.RowsofResult = this.Result.getRows().toArray();
        }
    }

    /**
     * Constructor of BQResultset, that initializes all private variables
     *
     * @param bigQueryGetQueryResultResponse BigQueryGetQueryResultResponse from Bigquery
     * @param bqStatementRoot                Reference of the Statement that creates this Resultset
     */
    public BQScrollableResultSet(GetQueryResultsResponse bigQueryGetQueryResultResponse,
                                 BQStatementRoot bqStatementRoot) {
        logger.debug("Created Scrollable resultset TYPE_SCROLL_INSENSITIVE");
        this.Result = bigQueryGetQueryResultResponse;
        BigInteger maxrow;
        try {
            maxrow = BigInteger.valueOf(bqStatementRoot.getMaxRows());
            this.Result.setTotalRows(maxrow);
        } catch (SQLException e) {
        } // Should not happen.

        try {
            maxFieldSize = bqStatementRoot.getMaxFieldSize();
        } catch (SQLException e) {
            // Should not happen.
        }

        if (this.Result.getRows() == null) {
            this.RowsofResult = null;
        } else {
            this.RowsofResult = this.Result.getRows().toArray();
        }
        if (bqStatementRoot instanceof BQStatement) {
            this.Statementreference = (BQStatement) bqStatementRoot;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int findColumn(String columnLabel) throws SQLException {
        if (this.isClosed()) {
            throw new BQSQLException("This Resultset is Closed");
        }
        int columncount = this.getMetaData().getColumnCount();
        for (int i = 1; i <= columncount; i++) {
            if (this.getMetaData().getCatalogName(i).equals(columnLabel)) {
                return i;
            }
        }
        throw new BQSQLException("No Such column labeled: " + columnLabel);
    }

    /** {@inheritDoc} */
    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        if (this.isClosed()) {
            throw new BQSQLException("This Resultset is Closed");
        }
        return new BQResultsetMetaData(this.Result);
    }

    /** {@inheritDoc} */
    @Override
    public Object getObject(int columnIndex) throws SQLException {
        // to make the logfiles smaller!
        //logger.debug("Function call getObject columnIndex is: " + String.valueOf(columnIndex));
        this.closestrm();
        if (this.isClosed()) {
            throw new BQSQLException("This Resultset is Closed");
        }
        this.ThrowCursorNotValidExeption();
        if (this.RowsofResult == null) {
            throw new BQSQLException("There are no rows in this Resultset");
        }
        if (this.getMetaData().getColumnCount() < columnIndex
                || columnIndex < 1) {
            throw new BQSQLException("ColumnIndex is not valid");
        }
        String Columntype = this.Result.getSchema().getFields()
                .get(columnIndex - 1).getType();

        TableCell field = ((TableRow) this.RowsofResult[this.Cursor]).getF().get(columnIndex - 1);

        if (Data.isNull(field.getV())) {
            this.wasnull = true;
            return null;
        } else {
            String result = field.getV().toString();
            this.wasnull = false;
            try {
                if (Columntype.equals("STRING")) {
                    //removing the excess byte by the setmaxFiledSize
                    if (maxFieldSize == 0 || maxFieldSize == Integer.MAX_VALUE) {
                        return result;
                    } else {
                        try { //lets try to remove the excess bytes
                            return result.substring(0, maxFieldSize);
                        } catch (IndexOutOfBoundsException iout) {
                            //we don't need to remove any excess byte
                            return result;
                        }
                    }
                }
                if (Columntype.equals("FLOAT")) {
                    return Float.parseFloat(result);
                }
                if (Columntype.equals("BOOLEAN")) {
                    return Boolean.parseBoolean(result);
                }
                if (Columntype.equals("INTEGER")) {
                    return Long.parseLong(result);
                }
                if (Columntype.equals("TIMESTAMP")) {
                    long val = new BigDecimal(result).longValue() * 1000;
                    return new Timestamp(val);
                }
                throw new BQSQLException("Unsupported Type");
            } catch (NumberFormatException e) {
                throw new BQSQLException(e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Statement getStatement() throws SQLException {
        return this.Statementreference;
    }

    /** {@inheritDoc} */
    @Override
    public String getString(int columnIndex) throws SQLException {
        //to make the logfiles smaller!
        //logger.debug("Function call getString columnIndex is: " + String.valueOf(columnIndex));
        this.closestrm();
        this.ThrowCursorNotValidExeption();
        if (this.isClosed()) {
            throw new BQSQLException("This Resultset is Closed");
        }
        String result = (String) ((TableRow) this.RowsofResult[this.Cursor]).getF()
                .get(columnIndex - 1).getV();
        if (result == null) {
            this.wasnull = true;
        } else {
            this.wasnull = false;
        }
        //removing the excess byte by the setmaxFiledSize
        if (maxFieldSize == 0 || maxFieldSize == Integer.MAX_VALUE) {
            return result;
        } else {
            try { //lets try to remove the excess bytes
                return result.substring(0, maxFieldSize);
            } catch (IndexOutOfBoundsException iout) {
                //we don't need to remove any excess byte
                return result;
            }
        }
    }
}