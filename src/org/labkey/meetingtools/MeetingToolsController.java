/*
 * Copyright (c) 2017 LabKey Corporation
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

package org.labkey.meetingtools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.springframework.validation.BindException;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MeetingToolsController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(MeetingToolsController.class);
    public static final String NAME = "meetingtools";

    public MeetingToolsController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class GetViewSessionsAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiSimpleResponse execute(SimpleApiJsonForm form, BindException error)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            JSONArray jArray = new JSONArray();

            Set<String> rooms = MeetingToolsManager.getViewShare().getRoomList(getContainer());
            for(String room : rooms)
            {
                JSONObject roomJson = new JSONObject();
                roomJson.put("title", room);
                roomJson.put("createdBy", MeetingToolsManager.getViewShare().getRoomOwner(new Pair<>(getContainer(), room)));
                roomJson.put("status", MeetingToolsManager.getViewShare().getRoomConfig(new Pair<>(getContainer(), room)) != null ? "Active" : "In Setup");
                jArray.put(roomJson);
            }

            response.put("rooms", jArray);
            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ViewShareSetupAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiSimpleResponse execute(SimpleApiJsonForm form, BindException error)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            JSONObject inJson = form.getJsonObject();
            JSONArray jArray = new JSONArray();

            MeetingToolsManager.getViewShare().newRoom(new Pair<>(getContainer(), inJson.getString("title")), getUser());

            Study study = StudyService.get().getStudy(getContainer());
            List<? extends Dataset> datasets = study.getDatasets();
            for(Dataset ds : datasets)
            {
                JSONObject dJson = new JSONObject();
                dJson.put("type", "Dataset");
                dJson.put("id", ds.getDatasetId());
                dJson.put("label", ds.getLabel());
                dJson.put("queryName", ds.getName());
                jArray.put(dJson);
            }

            Collection<Report> r = ReportService.get().getReports(getUser(), getContainer());
            for(Report e : r)
            {
                JSONObject rJson = new JSONObject();
                rJson.put("type", "Report");
                rJson.put("id", e.getDescriptor().getReportId());
                rJson.put("label", e.getDescriptor().getReportName());
                rJson.put("queryName", e.getDescriptor().getProperty("queryName"));
                jArray.put(rJson);
            }

            response.put("options", jArray);

            return response;
        }
    }
}