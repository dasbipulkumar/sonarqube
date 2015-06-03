/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.usergroups.ws;

import java.util.List;
import javax.annotation.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService.NewAction;
import org.sonar.api.server.ws.WebService.NewController;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.utils.Paging;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.MyBatis;
import org.sonar.core.user.GroupMembershipQuery;
import org.sonar.core.user.UserMembershipDto;
import org.sonar.core.user.UserMembershipQuery;
import org.sonar.server.db.DbClient;
import org.sonar.server.user.UserSession;

public class UsersAction implements UserGroupsWsAction {

  private static final String PARAM_ID = "id";
  private static final String PARAM_SELECTED = "selected";

  private static final String SELECTION_ALL = "all";
  private static final String SELECTION_SELECTED = PARAM_SELECTED;
  private static final String SELECTION_DESELECTED = "deselected";

  private static final String FIELD_SELECTED = PARAM_SELECTED;
  private static final String FIELD_NAME = "name";
  private static final String FIELD_LOGIN = "login";

  private final DbClient dbClient;
  private final UserSession userSession;

  public UsersAction(DbClient dbClient, UserSession userSession) {
    this.dbClient = dbClient;
    this.userSession = userSession;
  }

  @Override
  public void define(NewController context) {
    NewAction action = context.createAction("users")
      .setDescription("Search for users with membership information with respect to a group.")
      .setHandler(this)
      .setResponseExample(getClass().getResource("example-users.json"))
      .setSince("5.2");

    action.createParam(PARAM_ID)
      .setDescription("A group ID")
      .setExampleValue("42")
      .setRequired(true);

    action.createParam(PARAM_SELECTED)
      .setDescription("If specified, only show users who belong to a group (selected=selected) or only those who do not (selected=deselected).")
      .setPossibleValues(SELECTION_SELECTED, SELECTION_DESELECTED, SELECTION_ALL)
      .setDefaultValue(SELECTION_ALL);

    action.addSearchQuery("freddy", "names", "logins");

    action.addPagingParams(25);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkLoggedIn().checkGlobalPermission(GlobalPermissions.SYSTEM_ADMIN);

    Long groupId = request.mandatoryParamAsLong(PARAM_ID);
    int pageSize = request.mandatoryParamAsInt(Param.PAGE_SIZE);
    int page = request.mandatoryParamAsInt(Param.PAGE);
    String queryString = request.param(Param.TEXT_QUERY);
    String selected = request.param(PARAM_SELECTED);

    UserMembershipQuery query = UserMembershipQuery.builder()
      .groupId(groupId)
      .memberSearch(queryString)
      .membership(getMembership(selected))
      .pageIndex(page)
      .pageSize(pageSize)
      .build();

    DbSession dbSession = dbClient.openSession(false);
    try {
      dbClient.groupDao().selectById(dbSession, groupId);
      int total = dbClient.groupMembershipDao().countMembers(dbSession, query);
      Paging paging = Paging.create(pageSize, page, total);
      List<UserMembershipDto> users = dbClient.groupMembershipDao().selectMembers(dbSession, query, paging.offset(), paging.pageSize());

      JsonWriter json = response.newJsonWriter().beginObject();
      writeMembers(json, users);
      writePaging(json, paging);
      json.endObject().close();
    } finally {
      MyBatis.closeQuietly(dbSession);
    }
  }

  private void writeMembers(JsonWriter json, List<UserMembershipDto> users) {
    json.name("users").beginArray();
    for (UserMembershipDto user : users) {
      json.beginObject()
        .prop(FIELD_LOGIN, user.getLogin())
        .prop(FIELD_NAME, user.getName())
        .prop(FIELD_SELECTED, user.getGroupId() != null)
        .endObject();
    }
    json.endArray();
  }

  private void writePaging(JsonWriter json, Paging paging) {
    json.prop(Param.PAGE, paging.pageIndex())
      .prop(Param.PAGE_SIZE, paging.pageSize())
      .prop("total", paging.total());
  }

  private String getMembership(@Nullable String selected) {
    String membership = GroupMembershipQuery.ANY;
    if (SELECTION_SELECTED.equals(selected)) {
      membership = GroupMembershipQuery.IN;
    } else if (SELECTION_DESELECTED.equals(selected)) {
      membership = GroupMembershipQuery.OUT;
    }
    return membership;
  }
}