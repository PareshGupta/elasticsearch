/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.query;

import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

/**
 * A query that wraps a filter and simply returns a constant score equal to the
 * query boost for every document in the filter.
 */
public class ConstantScoreQueryBuilder extends AbstractQueryBuilder<ConstantScoreQueryBuilder> {

    public static final String NAME = "constant_score";
    public static final ParseField QUERY_NAME_FIELD = new ParseField(NAME);
    public static final ConstantScoreQueryBuilder PROTOTYPE = new ConstantScoreQueryBuilder(EmptyQueryBuilder.PROTOTYPE);

    private static final ParseField INNER_QUERY_FIELD = new ParseField("filter", "query");

    private final QueryBuilder filterBuilder;

    /**
     * A query that wraps another query and simply returns a constant score equal to the
     * query boost for every document in the query.
     *
     * @param filterBuilder The query to wrap in a constant score query
     */
    public ConstantScoreQueryBuilder(QueryBuilder filterBuilder) {
        if (filterBuilder == null) {
            throw new IllegalArgumentException("inner clause [filter] cannot be null.");
        }
        this.filterBuilder = filterBuilder;
    }

    /**
     * @return the query that was wrapped in this constant score query
     */
    public QueryBuilder innerQuery() {
        return this.filterBuilder;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        builder.field(INNER_QUERY_FIELD.getPreferredName());
        filterBuilder.toXContent(builder, params);
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    public static ConstantScoreQueryBuilder fromXContent(QueryParseContext parseContext) throws IOException {
        XContentParser parser = parseContext.parser();

        QueryBuilder<?> query = null;
        boolean queryFound = false;
        String queryName = null;
        float boost = AbstractQueryBuilder.DEFAULT_BOOST;

        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (parseContext.isDeprecatedSetting(currentFieldName)) {
                // skip
            } else if (token == XContentParser.Token.START_OBJECT) {
                if (parseContext.parseFieldMatcher().match(currentFieldName, INNER_QUERY_FIELD)) {
                    if (queryFound) {
                        throw new ParsingException(parser.getTokenLocation(), "[" + ConstantScoreQueryBuilder.NAME + "]"
                                + " accepts only one 'filter' element.");
                    }
                    query = parseContext.parseInnerQueryBuilder();
                    queryFound = true;
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                            "[constant_score] query does not support [" + currentFieldName + "]");
                }
            } else if (token.isValue()) {
                if (parseContext.parseFieldMatcher().match(currentFieldName, AbstractQueryBuilder.NAME_FIELD)) {
                    queryName = parser.text();
                } else if (parseContext.parseFieldMatcher().match(currentFieldName, AbstractQueryBuilder.BOOST_FIELD)) {
                    boost = parser.floatValue();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                            "[constant_score] query does not support [" + currentFieldName + "]");
                }
            } else {
                throw new ParsingException(parser.getTokenLocation(), "unexpected token [" + token + "]");
            }
        }
        if (!queryFound) {
            throw new ParsingException(parser.getTokenLocation(), "[constant_score] requires a 'filter' element");
        }

        ConstantScoreQueryBuilder constantScoreBuilder = new ConstantScoreQueryBuilder(query);
        constantScoreBuilder.boost(boost);
        constantScoreBuilder.queryName(queryName);
        return constantScoreBuilder;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        Query innerFilter = filterBuilder.toFilter(context);
        if (innerFilter == null ) {
            // return null so that parent queries (e.g. bool) also ignore this
            return null;
        }
        return new ConstantScoreQuery(innerFilter);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(filterBuilder);
    }

    @Override
    protected boolean doEquals(ConstantScoreQueryBuilder other) {
        return Objects.equals(filterBuilder, other.filterBuilder);
    }

    @Override
    protected ConstantScoreQueryBuilder doReadFrom(StreamInput in) throws IOException {
        QueryBuilder innerFilterBuilder = in.readQuery();
        return new ConstantScoreQueryBuilder(innerFilterBuilder);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeQuery(filterBuilder);
    }

    @Override
    protected QueryBuilder<?> doRewrite(QueryRewriteContext queryRewriteContext) throws IOException {
        QueryBuilder rewrite = filterBuilder.rewrite(queryRewriteContext);
        if (rewrite != filterBuilder) {
            return new ConstantScoreQueryBuilder(rewrite);
        }
        return this;
    }
}
