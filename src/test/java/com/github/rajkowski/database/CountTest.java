/*
 * Copyright 2026 Matt Rajkowski (https://github.com/rajkowski)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rajkowski.database;

import java.util.Arrays;

import junit.framework.TestCase;

public class CountTest extends TestCase {

    public void testCountAggregateBuildsSqlAndParameters() {
        QuerySpec spec = DB.COUNT("*")
                .FROM("users")
                .WHERE("active = ?", true);

        assertEquals("SELECT COUNT(*) FROM users WHERE active = ?", spec.getSql());
        assertEquals(Arrays.asList(true), spec.getParameters());
    }

    public void testAvgMinMaxAggregateBuildsSqlAndParameters() {
        QuerySpec avg = DB.AVG("price")
                .FROM("orders")
                .WHERE("status = ?", "paid");
        QuerySpec min = DB.MIN("price").FROM("orders");
        QuerySpec max = DB.MAX("price").FROM("orders");

        assertEquals("SELECT AVG(price) FROM orders WHERE status = ?", avg.getSql());
        assertEquals(Arrays.asList("paid"), avg.getParameters());
        assertEquals("SELECT MIN(price) FROM orders", min.getSql());
        assertEquals(Arrays.asList(), min.getParameters());
        assertEquals("SELECT MAX(price) FROM orders", max.getSql());
        assertEquals(Arrays.asList(), max.getParameters());
    }

    public void testSelectDistinctExtractOrderByBuildsSqlAndParameters() {
        QuerySpec spec = DB.SELECT("DISTINCT(EXTRACT(YEAR FROM start_date)) AS year")
                .FROM("table")
                .WHERE("folder_id = ?", 42L)
                .ORDER_BY("year DESC");

        assertEquals(
                "SELECT DISTINCT(EXTRACT(YEAR FROM start_date)) AS year FROM table WHERE folder_id = ? ORDER BY year DESC",
                spec.getSql());
        assertEquals(Arrays.asList(42L), spec.getParameters());
    }
}
