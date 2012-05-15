/*
 * Copyright 2012 Janrain, Inc.
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

package com.janrain.backplane2.server.dao;

import com.janrain.backplane2.server.Access;
import com.janrain.backplane2.server.Grant;
import com.janrain.backplane2.server.Token;
import com.janrain.backplane2.server.TokenAnonymous;
import com.janrain.backplane2.server.config.Backplane2Config;
import com.janrain.commons.supersimpledb.SimpleDBException;
import com.janrain.commons.supersimpledb.SuperSimpleDB;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.janrain.backplane2.server.config.Backplane2Config.SimpleDBTables.BP_ACCESS_TOKEN;

/**
 * @author Tom Raney
 */

public class TokenDAO extends DAO<Token> {

    TokenDAO(SuperSimpleDB superSimpleDB, Backplane2Config bpConfig) {
        super(superSimpleDB, bpConfig);
    }

    @Override
    public void persist(Token token) throws SimpleDBException {
        superSimpleDB.store(bpConfig.getTableName(BP_ACCESS_TOKEN), Token.class, token, true);
    }

    @Override
    public void delete(String id) throws SimpleDBException {
        deleteTokenById(id);
    }

    public Token retrieveToken(String tokenId) throws SimpleDBException {
        try{
            return superSimpleDB.retrieve(bpConfig.getTableName(BP_ACCESS_TOKEN), Token.TYPE.fromTokenString(tokenId).getTokenClass(), tokenId);
        } catch (IllegalArgumentException e) {
            logger.error("invalid token! => '" + tokenId + "'");
            return null; //invalid token id, don't even try
        }
    }

    public void deleteTokenById(String tokenId) throws SimpleDBException {
        superSimpleDB.delete(bpConfig.getTableName(BP_ACCESS_TOKEN), tokenId);
        // TODO: be sure to update grant that may refer to this token?
    }

    public void deleteExpiredTokens() throws SimpleDBException {
        try {
            logger.info("Backplane token cleanup task started.");
            String expiredClause = Access.Field.EXPIRES.getFieldName() + " < '" + Backplane2Config.ISO8601.format(new Date(System.currentTimeMillis())) + "'";
            superSimpleDB.deleteWhere(bpConfig.getTableName(BP_ACCESS_TOKEN), expiredClause);
        } catch (Exception e) {
            // catch-all, else cleanup thread stops
            logger.error("Backplane token cleanup task error: " + e.getMessage(), e);
        } finally {
            logger.info("Backplane token cleanup task finished.");
        }
    }

    public List<Token> retrieveTokensByGrant(Grant grant) throws SimpleDBException {
        ArrayList<Token> tokens = new ArrayList<Token>();

        // TODO: brittle - possible that a token doesn't exist for a given id?
        for (String tokenId : grant.getIssuedTokenIds()) {
            Token token = retrieveToken(tokenId);
            if (token != null) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    public void revokeTokenByGrant(Grant grant) throws SimpleDBException {
        List<Token> tokens = retrieveTokensByGrant(grant);
        for (Token token : tokens) {
            delete(token.getIdValue());
            logger.info("revoked token " + token.getIdValue());
        }
        logger.info("all tokens for grant " + grant.getIdValue() + " have been revoked");
    }

    public boolean isValidBinding(String channel, String bus) throws SimpleDBException {

        List<TokenAnonymous> tokens = superSimpleDB.retrieveWhere(bpConfig.getTableName(BP_ACCESS_TOKEN),
                TokenAnonymous.class, "channel='" + channel + "'", true);

        if (tokens == null || tokens.isEmpty()) {
            logger.error("No (anonymous) tokens found to bind channel " + channel + " to bus " + bus);
            return false;
        }

        for (TokenAnonymous token : tokens) {
            if ( ! token.getBus().equals(bus) ) {
                logger.error("Channel " + channel + " bound to bus " + token.getBus() + " (token: " +token.getIdValue() + "), not " + bus);
                return false;
            }
        }
        return true;
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(TokenDAO.class);

}
