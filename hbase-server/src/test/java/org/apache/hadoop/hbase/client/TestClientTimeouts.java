/**
 * Copyright The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.client;

import static org.junit.Assert.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.MasterAdminProtocol;
import org.apache.hadoop.hbase.MasterMonitorProtocol;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.MediumTests;
import org.apache.hadoop.hbase.ipc.RandomTimeoutRpcEngine;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(MediumTests.class)
public class TestClientTimeouts {
  final Log LOG = LogFactory.getLog(getClass());
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  protected static int SLAVES = 1;

 /**
   * @throws java.lang.Exception
   */
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Configuration conf = TEST_UTIL.getConfiguration();
    TEST_UTIL.startMiniCluster(SLAVES);
  }

  /**
   * @throws java.lang.Exception
   */
  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  /**
   * Test that a client that fails an RPC to the master retries properly and
   * doesn't throw any unexpected exceptions.
   * @throws Exception
   */
  @Test
  public void testAdminTimeout() throws Exception {
    long lastLimit = HConstants.DEFAULT_HBASE_CLIENT_PREFETCH_LIMIT;
    HConnection lastConnection = null;
    boolean lastFailed = false;
    int initialInvocations = RandomTimeoutRpcEngine.getNumberOfInvocations();

    RandomTimeoutRpcEngine engine = new RandomTimeoutRpcEngine(TEST_UTIL.getConfiguration());
    try {
      for (int i = 0; i < 5 || (lastFailed && i < 100); ++i) {
        lastFailed = false;
        // Ensure the HBaseAdmin uses a new connection by changing Configuration.
        Configuration conf = HBaseConfiguration.create(TEST_UTIL.getConfiguration());
        conf.setLong(HConstants.HBASE_CLIENT_PREFETCH_LIMIT, ++lastLimit);
        try {
          HBaseAdmin admin = new HBaseAdmin(conf);
          HConnection connection = admin.getConnection();
          assertFalse(connection == lastConnection);
          lastConnection = connection;
          // override the connection's rpc engine for timeout testing
          ((HConnectionManager.HConnectionImplementation)connection).setRpcEngine(engine);
          // run some admin commands
          HBaseAdmin.checkHBaseAvailable(conf);
          admin.setBalancerRunning(false, false);
        } catch (MasterNotRunningException ex) {
          // Since we are randomly throwing SocketTimeoutExceptions, it is possible to get
          // a MasterNotRunningException.  It's a bug if we get other exceptions.
          lastFailed = true;
        }
      }
      // Ensure the RandomTimeoutRpcEngine is actually being used.
      assertFalse(lastFailed);
      assertTrue(RandomTimeoutRpcEngine.getNumberOfInvocations() > initialInvocations);
    } finally {
      engine.close();
    }
  }
}