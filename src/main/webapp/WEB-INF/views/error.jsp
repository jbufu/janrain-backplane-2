<%@page import="org.apache.log4j.Logger"%>
<%@ include file="/WEB-INF/views/include.jsp" 
%><%@page contentType="text/plain" 
%><%--
  ~ Copyright 2011 Janrain, Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~    http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<c:choose><c:when test="${not empty message}">${message}</c:when>
<c:otherwise>Unable to process request.</c:otherwise></c:choose>