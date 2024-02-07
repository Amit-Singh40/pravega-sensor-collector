/**
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.sensor.collector.util;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class NonTransactionalEventWriterTest {

    @Test
    public void testCreateNonTransactionalEventWriterWithNull() {
        Exception exception = Assert.assertThrows(NullPointerException.class, () -> new NonTransactionalEventWriter(null));
        Assert.assertTrue("writer".equals(exception.getMessage()));
    }
}