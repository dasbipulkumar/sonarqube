/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.qualityprofile.ws;

import java.util.Date;
import java.util.Map;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService.NewAction;
import org.sonar.api.server.ws.WebService.NewController;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.qualityprofile.QProfileChangeQuery;
import org.sonar.db.qualityprofile.QualityProfileDto;

import static org.sonar.server.es.SearchOptions.MAX_LIMIT;

public class ChangelogAction implements QProfileWsAction {

  private static final String PARAM_SINCE = "since";
  private static final String PARAM_TO = "to";

  private final ChangelogLoader changelogLoader;
  private final QProfileFinder profileFinder;

  public ChangelogAction(ChangelogLoader changelogLoader, QProfileFinder profileFinder) {
    this.changelogLoader = changelogLoader;
    this.profileFinder = profileFinder;
  }

  @Override
  public void define(NewController context) {
    NewAction wsAction = context.createAction("changelog")
      .setSince("5.2")
      .setDescription("Get the history of changes on a quality profile: rule activation/deactivation, change in parameters/severity. " +
        "Events are ordered by date in descending order (most recent first).")
      .setHandler(this)
      .setResponseExample(getClass().getResource("example-changelog.json"));

    profileFinder.defineProfileParams(wsAction);

    wsAction.addPagingParams(50, MAX_LIMIT);

    wsAction.createParam(PARAM_SINCE)
      .setDescription("Start date for the changelog.")
      .setExampleValue("2011-04-25T01:15:42+0100");

    wsAction.createParam(PARAM_TO)
      .setDescription("End date for the changelog.")
      .setExampleValue("2013-07-25T07:35:42+0200");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    QualityProfileDto profile = profileFinder.find(request);

    QProfileChangeQuery query = new QProfileChangeQuery(profile.getKey());
    Date since = request.paramAsDateTime(PARAM_SINCE);
    if (since != null) {
      query.setFromIncluded(since.getTime());
    }
    Date to = request.paramAsDateTime(PARAM_TO);
    if (to != null) {
      query.setToExcluded(to.getTime());
    }
    int page = request.mandatoryParamAsInt(Param.PAGE);
    int pageSize = request.mandatoryParamAsInt(Param.PAGE_SIZE);
    query.setPage(page, pageSize);

    ChangelogLoader.Changelog changelog = changelogLoader.load(query);
    writeResponse(response.newJsonWriter(), page, pageSize, changelog);
  }

  private void writeResponse(JsonWriter json, int page, int pageSize, ChangelogLoader.Changelog changelog) {
    json.beginObject();
    json.prop("total", changelog.getTotal());
    json.prop(Param.PAGE, page);
    json.prop(Param.PAGE_SIZE, pageSize);
    json.name("events").beginArray();
    for (ChangelogLoader.Change change : changelog.getChanges()) {
      json.beginObject()
        .prop("date", DateUtils.formatDateTime(change.getCreatedAt()))
        .prop("authorLogin", change.getUserLogin())
        .prop("authorName", change.getUserName())
        .prop("action", change.getType())
        .prop("ruleKey", change.getRuleKey() == null ? null : change.getRuleKey().toString())
        .prop("ruleName", change.getRuleName());
      writeParameters(json, change);
      json.endObject();
    }
    json.endArray();
    json.endObject().close();
  }

  private void writeParameters(JsonWriter json, ChangelogLoader.Change change) {
    json.name("params").beginObject()
      .prop("severity", change.getSeverity());
    for (Map.Entry<String, String> param : change.getParams().entrySet()) {
      json.prop(param.getKey(), param.getValue());
    }
    json.endObject();
  }

}
