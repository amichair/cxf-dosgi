/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.dosgi.itests.multi.customintent;

import java.lang.reflect.Method;

import org.apache.cxf.dosgi.samples.soap.Task;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChangeTitleInterceptor extends AbstractPhaseInterceptor<Message> {
    Logger log = LoggerFactory.getLogger(ChangeTitleInterceptor.class);

    ChangeTitleInterceptor() {
        super(Phase.USER_LOGICAL);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            MessageContentsList contents = MessageContentsList.getContentsList(message);
            Object response = contents.get(0);
            Method method = response.getClass().getMethod("getReturn");
            Task task = (Task)method.invoke(response);
            task.setTitle("changed");
        } catch (Exception e) {
            log.warn("Error in interceptor", e);
        }

    }
}
