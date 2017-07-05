/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.workspace.events;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.core.jsonrpc.commons.RequestHandlerConfigurator;
import org.eclipse.che.api.workspace.shared.dto.event.WorkspaceStatusEvent;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.workspace.event.WorkspaceRunningEvent;
import org.eclipse.che.ide.api.workspace.event.WorkspaceStartedEvent;
import org.eclipse.che.ide.api.workspace.event.WorkspaceStartingEvent;
import org.eclipse.che.ide.api.workspace.event.WorkspaceStatusChangedEvent;
import org.eclipse.che.ide.api.workspace.event.WorkspaceStoppedEvent;
import org.eclipse.che.ide.api.workspace.event.WorkspaceStoppingEvent;
import org.eclipse.che.ide.context.AppContextImpl;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.workspace.WorkspaceServiceClient;

import static com.google.common.base.Strings.nullToEmpty;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.RUNNING;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.STARTING;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.STOPPED;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.STOPPING;

/**
 * Receives notifications about changing workspace's status.
 * After a notification is received it is processed and
 * an appropriate event is fired on the {@link EventBus}.
 */
@Singleton
class WorkspaceStatusEventHandler {

    private final WorkspaceServiceClient workspaceServiceClient;
    private final AppContext             appContext;
    private final EventBus               eventBus;

    @Inject
    WorkspaceStatusEventHandler(RequestHandlerConfigurator configurator,
                                WorkspaceServiceClient workspaceServiceClient,
                                AppContext appContext,
                                EventBus eventBus) {
        this.workspaceServiceClient = workspaceServiceClient;
        this.appContext = appContext;
        this.eventBus = eventBus;

        configurator.newConfiguration()
                    .methodName("workspace/statusChanged")
                    .paramsAsDto(WorkspaceStatusEvent.class)
                    .noResult()
                    .withBiConsumer((endpointId, event) -> {
                        Log.debug(getClass(), "Received notification from endpoint: " + endpointId);

                        processStatus(event);
                    });
    }

    private void processStatus(WorkspaceStatusEvent event) {
        // fire deprecated WorkspaceStatusChangedEvent for backward compatibility with IDE 5.x
        eventBus.fireEvent(new WorkspaceStatusChangedEvent(event));

        workspaceServiceClient.getWorkspace(appContext.getWorkspaceId()).then(workspace -> {
            // Update workspace model in AppContext before firing an event.
            // Because AppContext always must return an actual workspace model.
            ((AppContextImpl)appContext).setWorkspace(workspace);

            if (event.getStatus() == STARTING) {
                eventBus.fireEvent(new WorkspaceStartingEvent(workspace));
            } else if (event.getStatus() == RUNNING) {
                eventBus.fireEvent(new WorkspaceRunningEvent());

                // fire deprecated WorkspaceStatusChangedEvent for backward compatibility with IDE 5.x
                eventBus.fireEvent(new WorkspaceStartedEvent(workspace));
            } else if (event.getStatus() == STOPPING) {
                eventBus.fireEvent(new WorkspaceStoppingEvent());
            } else if (event.getStatus() == STOPPED) {
                eventBus.fireEvent(new WorkspaceStoppedEvent(workspace, event.getError() != null, nullToEmpty(event.getError())));
            }
        });
    }
}
