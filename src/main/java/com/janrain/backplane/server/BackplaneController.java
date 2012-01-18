/*
 * Copyright 2011 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janrain.backplane.server;

import com.janrain.backplane.server.config.*;
import com.janrain.backplane.server.metrics.MetricsAccumulator;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.SuperSimpleDB;
import com.janrain.crypto.ChannelUtil;
import com.janrain.crypto.HmacHashUtils;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.HistogramMetric;
import com.yammer.metrics.core.MeterMetric;
import com.yammer.metrics.core.TimerMetric;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Backplane API implementation.
 *
 * @author Johnny Bufu
 */
@Controller
@RequestMapping(value="/*")
@SuppressWarnings({"UnusedDeclaration"})
public class BackplaneController {

    // - PUBLIC

    @RequestMapping(value = "/", method = { RequestMethod.GET, RequestMethod.HEAD })
    public ModelAndView greetings(HttpServletRequest request, HttpServletResponse response) {
        if (RequestMethod.HEAD.toString().equals(request.getMethod())) {
            response.setContentLength(0);
        }
        return new ModelAndView("welcome");
    }

    /**
     * Handle dynamic discovery of this server's registration endpoint
     * @return
     */
    @RequestMapping(value = "/.well-known/host-meta", method = { RequestMethod.GET})
    public ModelAndView xrds(HttpServletRequest request, HttpServletResponse response) {

        ModelAndView view = new ModelAndView("xrd");
        view.addObject("host", "http://" + request.getServerName());
        view.addObject("secureHost", "https://" + request.getServerName());
        return view;
    }


    /**
     * The OAuth "Token Endpoint" is used to obtain an access token to be used
     * for retrieving messages from the Get Messages endpoint.
     *
     * @param request
     * @param response
     * @param client_id
     * @param grant_type
     * @param redirect_uri
     * @param code
     * @param client_secret
     * @param scope
     * @param callback (optional callback function)
     * @return
     * @throws AuthException
     * @throws SimpleDBException
     * @throws BackplaneServerException
     */

    @RequestMapping(value = "/token", method = { RequestMethod.POST})
    @ResponseBody
    public HashMap<String,Object> token(HttpServletRequest request, HttpServletResponse response,
                                        @RequestParam(value = "client_id", required = false) String client_id,
                                        @RequestParam(value = "grant_type", required = false) String grant_type,
                                        @RequestParam(value = "redirect_uri", required = false) String redirect_uri,
                                        @RequestParam(value = "code", required = false) String code,
                                        @RequestParam(value = "client_secret", required = false) String client_secret,
                                        @RequestParam(value = "scope", required = false) String scope,
                                        @RequestParam(required = false) String callback)
            throws AuthException, SimpleDBException, BackplaneServerException {

        TokenRequest tokenRequest = new TokenRequest(client_id, grant_type, redirect_uri,
                                                        code, client_secret, scope, callback);

        try {
            tokenRequest.setCode(superSimpleDb.retrieve(bpConfig.getCodeTableName(), Code.class, code));
        } catch (Exception e) {
            //do nothing
        }

        try {
            if (StringUtils.isNotEmpty(client_id) && !client_id.equals(Token.ANONYMOUS))
            tokenRequest.setClient(superSimpleDb.retrieve(bpConfig.getClientsTableName(), Client.class, client_id));
        } catch (Exception e) {
            //do nothing
            logger.info("could not retrieve client with id '" + client_id + "'", e);
        }

        HashMap errors = tokenRequest.validate();
        if (errors != null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return errors;
        }

        try {
            return new OAuth2Response(tokenDao, tokenRequest).generateResponse();
        } catch (final BackplaneServerException bpe) {
            return new HashMap<String,Object>() {{
                put(ERR_MSG_FIELD, bpe.getMessage());
            }};

        }

    }

    /**
     * Retrieve messages from the server.
     * @param request
     * @param response
     * @return
     */

    @RequestMapping(value = "/messages", method = { RequestMethod.GET})
    public @ResponseBody HashMap<String,Object> messages(HttpServletRequest request, HttpServletResponse response,
                                @RequestParam(value = "access_token", required = false) String access_token,
                                @RequestParam(value = "block", defaultValue = "0", required = false) Integer block,
                                @RequestParam(required = false) String callback,
                                @RequestParam(value = "since", required = false) String since)
            throws SimpleDBException, BackplaneServerException {

        //TODO: add since and callback support - support for block?

        MessageRequest messageRequest = new MessageRequest(access_token, callback);

        try {
            messageRequest.setToken(tokenDao.retrieveToken(access_token));
        } catch (SimpleDBException e) {
            //do nothing
            logger.info("Could not retrieve token " + access_token,e);
        }

        List<BackplaneMessage> messages = superSimpleDb.retrieveWhere(bpConfig.getMessagesTableName(),
                BackplaneMessage.class, messageRequest.getToken().getScope().buildQueryFromScope(), true);

        if (messages.isEmpty()) {
            return null;
        }

        String nextUrl = "https://" + request.getServerName() + "/v2/messages?since=" + messages.get(messages.size()-1).getIdValue();
        List<Map<String,Object>> frames = new ArrayList<Map<String, Object>>();

        for (BackplaneMessage message : messages) {
            frames.add(message.asFrame(request.getServerName(), messageRequest.getToken().isPrivileged()));
        }

        HashMap<String, Object> hash = new HashMap<String, Object>();
        hash.put("nextURL", nextUrl);
        hash.put("messages", frames);

        return hash;


       // throw new NotImplementedException();

    }



    /**
     * Retrieve a single message from the server.
     * @param request
     * @param response
     * @return
     */

    @RequestMapping(value = "/message/{msg_id}", method = { RequestMethod.GET})
    public @ResponseBody HashMap<String,Object> message(HttpServletRequest request, HttpServletResponse response,
                                                        @PathVariable String msg_id,
                                                        @RequestParam(value = "access_token", required = false) String access_token,
                                                        @RequestParam(required = false) String callback)
            throws BackplaneServerException {

        MessageRequest messageRequest = new MessageRequest(access_token, callback);

        BackplaneMessage message = null;

        try {
            messageRequest.setToken(tokenDao.retrieveToken(access_token));
        } catch (SimpleDBException e) {
            //do nothing
            logger.info("Could not retrieve token " + access_token,e);
        }

        try {
            message = superSimpleDb.retrieve(bpConfig.getMessagesTableName(), BackplaneMessage.class, msg_id);
        } catch (SimpleDBException e) {
            logger.info("Could not find message " + msg_id,e);
        }

        if (message == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return new HashMap<String,Object>() {{
                put(ERR_MSG_FIELD, "Message not found");
            }};
        } else {
            // For the standard request, verify that the token channel matches the message channel or return error
            if (!messageRequest.getToken().isPrivileged() && !message.get(BackplaneMessage.Field.CHANNEL).equals(messageRequest.getToken().getChannelName())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return new HashMap<String,Object>() {{
                    put(ERR_MSG_FIELD, "Forbidden");
                }};
            }
            // For the privileged request, make sure the message bus matches a bus in the token
            if (messageRequest.getToken().isPrivileged() && !messageRequest.getToken().isAllowedBus(message.get(BackplaneMessage.Field.BUS))) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return new HashMap<String,Object>() {{
                    put(ERR_MSG_FIELD, "Forbidden");
                }};
            }
        }

        HashMap errors = messageRequest.validate();
        if (errors != null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return errors;
        }

        if (StringUtils.isBlank(callback)) {
            response.setContentType("application/json");
            return message.asFrame(request.getServerName(), messageRequest.getToken().isPrivileged());
        } else {
            response.setContentType("application/x-javascript");
            try {
                String responseBody = callback + "(" + new String(new ObjectMapper().writeValueAsString(message.asFrame("https://" +
                        request.getServerName() + "/v2/message", messageRequest.getToken().isPrivileged())) + ")");

                response.getWriter().print(responseBody);

                return null;
            } catch (IOException e) {
                String errMsg = "Error converting frames to JSON: " + e.getMessage();
                logger.error(errMsg, bpConfig.getDebugException(e));
                throw new BackplaneServerException(errMsg, e);
            }
        }



    }

    /**
     * Publish messages to the Backplane.
     * @param request
     * @param response
     * @return
     */

    @RequestMapping(value = "/messages", method = { RequestMethod.POST})
    public ModelAndView postMessages(HttpServletRequest request, HttpServletResponse response,
                                     @RequestParam(value = "access_token", required = false) String access_token,
                                     @RequestParam(required = false) String callback,
                                     @RequestParam(required = false) String since) {



        throw new NotImplementedException();

    }





    @RequestMapping(value = "/bus/{bus}", method = RequestMethod.GET)
    public @ResponseBody List<HashMap<String,Object>> getBusMessages(
                                @RequestHeader(value = "Authorization", required = false) String basicAuth,
                                @PathVariable String bus,
                                @RequestParam(value = "since", defaultValue = "") String since,
                                @RequestParam(value = "sticky", required = false) String sticky )
        throws AuthException, SimpleDBException, BackplaneServerException {

        checkAuth(basicAuth, bus, BackplaneConfig.BUS_PERMISSION.GETALL);


        // log metric
        busGets.mark();

        if (! StringUtils.isBlank(sticky) &&  "true".equalsIgnoreCase(sticky)) {
            busGetsSticky.mark();
        }

        StringBuilder whereClause = new StringBuilder()
            .append(BackplaneMessage.Field.BUS.getFieldName()).append("='").append(bus).append("'");
        if (! StringUtils.isEmpty(since)) {
            whereClause.append(" and ").append(BackplaneMessage.Field.ID.getFieldName()).append(" > '").append(since).append("'");
        }
        if (! StringUtils.isEmpty(sticky)) {
            whereClause.append(" and ").append(BackplaneMessage.Field.STICKY.getFieldName()).append("='").append(sticky).append("'");
        }

        List<BackplaneMessage> messages = superSimpleDb.retrieveWhere(bpConfig.getMessagesTableName(), BackplaneMessage.class, whereClause.toString(), true);

        List<HashMap<String,Object>> frames = new ArrayList<HashMap<String, Object>>();
        for (BackplaneMessage message : messages) {
            frames.add(message.asFrame("", true));
        }
        return frames;

    }

    @RequestMapping(value = "/bus/{bus}/channel/{channel}", method = RequestMethod.GET)
    public ResponseEntity<String> getChannel(
                                @PathVariable String bus,
                                @PathVariable String channel,
                                @RequestParam(required = false) String callback,
                                @RequestParam(value = "since", required = false) String since,
                                @RequestParam(value = "sticky", required = false) String sticky )
        throws SimpleDBException, AuthException, BackplaneServerException {

        // log metric
        channelGets.mark();

        if (! StringUtils.isBlank(sticky) &&  "true".equalsIgnoreCase(sticky)) {
            channelGetsSticky.mark();
        }

        if (StringUtils.isBlank(callback)) {
            return new ResponseEntity<String>(
                    NEW_CHANNEL_LAST_PATH.equals(channel) ? newChannel() : getChannelMessages(bus, channel, since, sticky),
                    new HttpHeaders() {{
                        add("Content-Type", "application/json");
                    }},
                    HttpStatus.OK);

        } else {
            return new ResponseEntity<String>(
                    paddedResponse(callback, NEW_CHANNEL_LAST_PATH.equals(channel) ? newChannel() : getChannelMessages(bus, channel, since, sticky)),
                    new HttpHeaders() {{
                        add("Content-Type", "application/x-javascript");
                    }},
                    HttpStatus.OK);
        }
    }

    @RequestMapping(value = "/bus/{bus}/channel/{channel}", method = RequestMethod.POST)
    public @ResponseBody String postToChannel(
                                @RequestHeader(value = "Authorization", required = false) String basicAuth,
                                @RequestBody List<Map<String,Object>> messages,
                                @PathVariable String bus,
                                @PathVariable String channel) throws AuthException, SimpleDBException, BackplaneServerException {
        checkAuth(basicAuth, bus, BackplaneConfig.BUS_PERMISSION.POST);

        //Block post if the caller has exceeded the message post limit
        Long count = superSimpleDb.retrieveCount(bpConfig.getMessagesTableName(),
                "select count(*) from `" + bpConfig.getMessagesTableName() + "` where bus='" + bus + "' and channel_name='" + channel + "'");

        if (count >= bpConfig.getDefaultMaxMessageLimit()) {
            throw new BackplaneServerException("Message limit exceeded for this channel");
        }

        //log metric - although this metric may need to be seeded on instance startup to be accurate
        messagesPerChannel.update(count);

        //log metric
        posts.mark();

        for(Map<String,Object> messageData : messages) {
            BackplaneMessage message = new BackplaneMessage(generateMessageId(), bus, channel, messageData);
            superSimpleDb.store(bpConfig.getMessagesTableName(), BackplaneMessage.class, message, true); // todo: make long entries support configurable
        }

        return "";
    }

    /**
     * Handle auth errors
     */
    @ExceptionHandler
    @ResponseBody
    public Map<String, String> handle(final AuthException e, HttpServletResponse response) {
        logger.error("Backplane authentication error: " + bpConfig.getDebugException(e));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return new HashMap<String,String>() {{
            put(ERR_MSG_FIELD, e.getMessage());
        }};
    }

    /**
     * Handle all other errors
     */
    @ExceptionHandler
    @ResponseBody
    public Map<String, String> handle(final Exception e, HttpServletResponse response) {
        logger.error("Error handling backplane request", bpConfig.getDebugException(e));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return new HashMap<String,String>() {{
            try {
                put(ERR_MSG_FIELD, bpConfig.isDebugMode() ? e.getMessage() : "Error processing request.");
            } catch (SimpleDBException e1) {
                put(ERR_MSG_FIELD, "Error processing request.");
            }
        }};
    }

    /**
     * @return a time-based, lexicographically comparable message ID.
     */
    public static String generateMessageId() {
        return BackplaneConfig.ISO8601.format(new Date()) + "-" + ChannelUtil.randomString(10);
    }

    /*
    public static String randomString(int length) {
        byte[] randomBytes = new byte[length];
        // the base64 character set per RFC 4648 with last two members '-' and '_' removed due to possible
        // compatibility issues.
        byte[] digits = {'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T',
                         'U','V','W','X','Y','Z','a','b','c','d','e','f','g','h','i','j','k','l','m','n',
                         'o','p','q','r','s','t','u','v','w','x','y','z','0','1','2','3','4','5','6','7',
                         '8','9'};
        random.nextBytes(randomBytes);
        for (int i = 0; i < length; i++) {
            byte b = randomBytes[i];
            int c = Math.abs(b % digits.length);
            randomBytes[i] = digits[c];
        }
        try {
            return new String(randomBytes, "US-ASCII");
        }
        catch (UnsupportedEncodingException e) {
            logger.error("US-ASCII character encoding not supported", e); // shouldn't happen
            return null;
        }
    }
    */
    
    // - PRIVATE

    private static final Logger logger = Logger.getLogger(BackplaneController.class);

    private static final String NEW_CHANNEL_LAST_PATH = "new";
    private static final String ERR_MSG_FIELD = "error";
    private static final String ERR_MSG_DESCRIPTION = "error_description";
    private static final int CHANNEL_NAME_LENGTH = 32;

    private final MeterMetric posts =
            Metrics.newMeter(BackplaneController.class, "post", "posts", TimeUnit.MINUTES);

    private final MeterMetric channelGets =
            Metrics.newMeter(BackplaneController.class, "channel_get", "channel_gets", TimeUnit.MINUTES);
    private final MeterMetric channelGetsSticky = Metrics.newMeter(BackplaneController.class, "channel_gets_sticky", "channel_gets_sticky", TimeUnit.MINUTES);


    private final MeterMetric busGets =
            Metrics.newMeter(BackplaneController.class, "bus_get", "bus_gets", TimeUnit.MINUTES);
    private final MeterMetric busGetsSticky = Metrics.newMeter(BackplaneController.class, "bus_gets_sticky", "bus_gets_sticky", TimeUnit.MINUTES);

    private final TimerMetric getMessagesTime =
            Metrics.newTimer(BackplaneController.class, "get_messages_time", TimeUnit.MILLISECONDS, TimeUnit.MINUTES);

    private final HistogramMetric payLoadSizesOnGets = Metrics.newHistogram(BackplaneController.class, "payload_sizes_gets");

    private final HistogramMetric messagesPerChannel = Metrics.newHistogram(BackplaneController.class, "messages_per_channel");

    @Inject
    private BackplaneConfig bpConfig;

    @Inject
    private SuperSimpleDB superSimpleDb;

    @Inject
    private TokenDAO tokenDao;

    @Inject
    private MetricsAccumulator metricAccumulator;

    //private static final Random random = new SecureRandom();

    private void checkAuth(String basicAuth, String bus, BackplaneConfig.BUS_PERMISSION permission) throws AuthException {
        // authN
        String userPass = null;
        if ( basicAuth == null || ! basicAuth.startsWith("Basic ") || basicAuth.length() < 7) {
            authError("Invalid Authorization header: " + basicAuth);
        } else {
            try {
                userPass = new String(Base64.decodeBase64(basicAuth.substring(6).getBytes("utf-8")));
            } catch (UnsupportedEncodingException e) {
                authError("Cannot check authentication, unsupported encoding: utf-8"); // shouldn't happen
            }
        }

        @SuppressWarnings({"ConstantConditions"})
        int delim = userPass.indexOf(":");
        if (delim == -1) {
            authError("Invalid Basic auth token: " + userPass);
        }
        String user = userPass.substring(0, delim);
        String pass = userPass.substring(delim + 1);

        User userEntry = null;
        try {
            userEntry = bpConfig.getConfig(user, User.class);
        } catch (SimpleDBException e) {
            authError("Error looking up user: " + user);
        }

        if (userEntry == null) {
            authError("User not found: " + user);
        } else if ( ! HmacHashUtils.checkHmacHash(pass, userEntry.get(User.Field.PWDHASH)) ) {
            authError("Incorrect password for user " + user);
        }

        // authZ
        BusConfig busConfig = null;
        try {
            busConfig = bpConfig.getConfig(bus, BusConfig.class);
        } catch (SimpleDBException e) {
            authError("Error looking up bus configuration for " + bus);
        }
        if (busConfig == null) {
            authError("Bus configuration not found for " + bus);
        } else if (!busConfig.getPermissions(user).contains(permission)) {
            logger.error("User " + user + " denied " + permission + " to " + bus);
            throw new AuthException("Access denied.");
        }
    }

    private void authError(String errMsg) throws AuthException {
        logger.error(errMsg);
        try {
            throw new AuthException("Access denied. " + (bpConfig.isDebugMode() ? errMsg : ""));
        } catch (Exception e) {
            throw new AuthException("Access denied.");
        }
    }

    private String paddedResponse(String callback, String s) {
        if (StringUtils.isBlank(callback)) {
            throw new IllegalArgumentException("Callback cannot be blank.");
        }
        StringBuilder result = new StringBuilder(callback);
        result.append("(").append(s).append(")");
        return result.toString();
    }

    private String newChannel() {
        return "\"" + ChannelUtil.randomString(CHANNEL_NAME_LENGTH) +"\"";
    }

    private String getChannelMessages(final String bus, final String channel, final String since, final String sticky) throws SimpleDBException, BackplaneServerException {

        try {
            return getMessagesTime.time(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    StringBuilder whereClause = new StringBuilder()
                        .append(BackplaneMessage.Field.BUS.getFieldName()).append("='").append(bus).append("'")
                        .append(" and ").append(BackplaneMessage.Field.CHANNEL.getFieldName()).append("='").append(channel).append("'");
                    if (! StringUtils.isEmpty(since)) {
                        whereClause.append(" and ").append(BackplaneMessage.Field.ID.getFieldName()).append(" > '").append(since).append("'");
                    }
                    if (! StringUtils.isEmpty(sticky)) {
                        whereClause.append(" and ").append(BackplaneMessage.Field.STICKY.getFieldName()).append("='").append(sticky).append("'");
                    }

                    List<BackplaneMessage> messages = superSimpleDb.retrieveWhere(bpConfig.getMessagesTableName(), BackplaneMessage.class, whereClause.toString(), true);

                    List<Map<String,Object>> frames = new ArrayList<Map<String, Object>>();

                    for (BackplaneMessage message : messages) {
                        frames.add(message.asFrame("", true));
                    }

                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        String payload = mapper.writeValueAsString(frames);
                        payLoadSizesOnGets.update(payload.length());
                        return mapper.writeValueAsString(frames);
                    } catch (IOException e) {
                        String errMsg = "Error converting frames to JSON: " + e.getMessage();
                        logger.error(errMsg, bpConfig.getDebugException(e));
                        throw new BackplaneServerException(errMsg, e);
                    }
                }
            });
        } catch (SimpleDBException sdbe) {
            throw sdbe;
        } catch (BackplaneServerException bse) {
            throw bse;
        } catch (Exception e) {
            throw new BackplaneServerException(e.getMessage());
        }
    }
}
