/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomee.chatterbox.xmpp.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Template {

    public static final String LIMITED_REGEX_SUFFIX = "(/.*)?";
    public static final String FINAL_MATCH_GROUP = "FINAL_MATCH_GROUP";
    private static final String DEFAULT_PATH_VARIABLE_REGEX = "([^/]+?)";
    private static final String CHARACTERS_TO_ESCAPE = ".*+$()";

    private final String template;
    private final List<String> variables = new ArrayList<String>();
    private final List<String> customVariables = new ArrayList<String>();
    private final Pattern templateRegexPattern;
    private final String literals;
    private final List<UriChunk> uriChunks;

    public Template(final String theTemplate) {
        template = theTemplate;
        final StringBuilder literalChars = new StringBuilder();
        final StringBuilder patternBuilder = new StringBuilder();
        final CurlyBraceTokenizer tok = new CurlyBraceTokenizer(template);
        uriChunks = new ArrayList<>();
        while (tok.hasNext()) {
            final String templatePart = tok.next();
            final UriChunk chunk = UriChunk.createUriChunk(templatePart);
            uriChunks.add(chunk);
            if (chunk instanceof Literal) {
                final String substr = escapeCharacters(chunk.getValue());
                literalChars.append(substr);
                patternBuilder.append(substr);
            } else if (chunk instanceof Variable) {
                final Variable var = (Variable) chunk;
                variables.add(var.getName());
                if (var.getPattern() != null) {
                    customVariables.add(var.getName());
                    patternBuilder.append('(');
                    patternBuilder.append(var.getPattern());
                    patternBuilder.append(')');
                } else {
                    patternBuilder.append(DEFAULT_PATH_VARIABLE_REGEX);
                }
            }
        }
        literals = literalChars.toString();

        final int endPos = patternBuilder.length() - 1;
        final boolean endsWithSlash = (endPos >= 0) && patternBuilder.charAt(endPos) == '/';
        if (endsWithSlash) {
            patternBuilder.deleteCharAt(endPos);
        }
        patternBuilder.append(LIMITED_REGEX_SUFFIX);

        templateRegexPattern = Pattern.compile(patternBuilder.toString());
    }

    private static String escapeCharacters(final String expression) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expression.length(); i++) {
            final char ch = expression.charAt(i);
            sb.append(isReservedCharacter(ch) ? "\\" + ch : ch);
        }
        return sb.toString();
    }

    private static boolean isReservedCharacter(final char ch) {
        return CHARACTERS_TO_ESCAPE.indexOf(ch) != -1;
    }

    public String getLiteralChars() {
        return literals;
    }

    public String getValue() {
        return template;
    }

    public String getPatternValue() {
        return templateRegexPattern.toString();
    }

    public List<String> getVariables() {
        return Collections.unmodifiableList(variables);
    }

    public List<String> getCustomVariables() {
        return Collections.unmodifiableList(customVariables);
    }

    public boolean match(final String uri, final Map<String, List<String>> templateVariableToValue) {

        if (uri == null) {
            return (templateRegexPattern == null);
        }

        if (templateRegexPattern == null) {
            return false;
        }

        Matcher m = templateRegexPattern.matcher(uri);
        if (!m.matches()) {
            return false;
        }

        // Assign the matched template values to template variables
        final int groupCount = m.groupCount();

        int i = 1;
        for (final String name : variables) {
            while (i <= groupCount) {
                final String value = m.group(i++);
                if ((value == null || value.length() == 0 && i < groupCount)
                        && variables.size() + 1 < groupCount) {
                    continue;
                }

                if (templateVariableToValue.get(name) == null) {
                    templateVariableToValue.put(name, new ArrayList<>());
                }

                templateVariableToValue.get(name).add(value);
                break;
            }
        }
        // The right hand side value, might be used to further resolve
        // sub-resources.

        String finalGroup = m.group(groupCount);
        if (finalGroup == null) {
            finalGroup = "";
        }

        templateVariableToValue.put(FINAL_MATCH_GROUP, Collections.singletonList(finalGroup));
        return true;
    }

    public String substitute(final List<String> values) throws IllegalArgumentException {
        if (values == null) {
            throw new IllegalArgumentException("values is null");
        }
        final Iterator<String> iter = values.iterator();
        final StringBuilder sb = new StringBuilder();
        for (final UriChunk chunk : uriChunks) {
            if (chunk instanceof Variable) {
                final Variable var = (Variable) chunk;
                if (iter.hasNext()) {
                    final String value = iter.next();
                    if (!var.matches(value)) {
                        throw new IllegalArgumentException("Value '" + value + "' does not match variable "
                                + var.getName() + " with value "
                                + var.getPattern());
                    }
                    sb.append(value);
                } else {
                    sb.append(var);
                }
            } else {
                sb.append(chunk);
            }
        }
        return sb.toString();
    }

    String substitute(final Map<String, ?> valuesMap) throws IllegalArgumentException {
        return this.substitute(valuesMap, Collections.<String>emptySet(), false);
    }

    public String substitute(final Map<String, ?> valuesMap,
                             final Set<String> encodePathSlashVars,
                             final boolean allowUnresolved) throws IllegalArgumentException {
        if (valuesMap == null) {
            throw new IllegalArgumentException("valuesMap is null");
        }
        final StringBuilder sb = new StringBuilder();
        for (final UriChunk chunk : uriChunks) {
            if (chunk instanceof Variable) {
                final Variable var = (Variable) chunk;
                final Object value = valuesMap.get(var.getName());
                if (value != null) {
                    String sval = value.toString();
                    if (!var.matches(sval)) {
                        throw new IllegalArgumentException("Value '" + sval + "' does not match variable "
                                + var.getName() + " with value "
                                + var.getPattern());
                    }
                    if (encodePathSlashVars.contains(var.getName())) {
                        sval = sval.replaceAll("/", "%2F");
                    }
                    sb.append(sval);
                } else if (allowUnresolved) {
                    sb.append(chunk);
                } else {
                    throw new IllegalArgumentException("Template variable " + var.getName()
                            + " has no matching value");
                }
            } else {
                sb.append(chunk);
            }
        }
        return sb.toString();
    }

    private abstract static class UriChunk {
        public static UriChunk createUriChunk(final String uriChunk) {
            if (uriChunk == null || "".equals(uriChunk)) {
                throw new IllegalArgumentException("uriChunk is empty");
            }
            UriChunk uriChunkRepresentation = Variable.create(uriChunk);
            if (uriChunkRepresentation == null) {
                uriChunkRepresentation = Literal.create(uriChunk);
            }
            return uriChunkRepresentation;
        }

        public abstract String getValue();

        @Override
        public String toString() {
            return getValue();
        }
    }

    private static final class Literal extends UriChunk {
        private String value;

        private Literal() {
            // empty constructor
        }

        public static Literal create(final String uriChunk) {
            if (uriChunk == null || "".equals(uriChunk)) {
                throw new IllegalArgumentException("uriChunk is empty");
            }
            final Literal literal = new Literal();
            literal.value = uriChunk;
            return literal;
        }

        @Override
        public String getValue() {
            return value;
        }

    }

    private static final class Variable extends UriChunk {
        private static final Pattern VARIABLE_PATTERN = Pattern.compile("(\\w[-\\w\\.]*[ ]*)(\\:(.+))?");
        private String name;
        private Pattern pattern;

        private Variable() {
            // empty constructor
        }

        public static Variable create(String uriChunk) {
            final Variable newVariable = new Variable();
            if (uriChunk == null || "".equals(uriChunk)) {
                return null;
            }
            if (CurlyBraceTokenizer.insideBraces(uriChunk)) {
                uriChunk = CurlyBraceTokenizer.stripBraces(uriChunk).trim();
                final Matcher matcher = VARIABLE_PATTERN.matcher(uriChunk);
                if (matcher.matches()) {
                    newVariable.name = matcher.group(1).trim();
                    if (matcher.group(2) != null && matcher.group(3) != null) {
                        final String patternExpression = matcher.group(3).trim();
                        newVariable.pattern = Pattern.compile(patternExpression);
                    }
                    return newVariable;
                }
            }
            return null;
        }

        public String getName() {
            return name;
        }

        public String getPattern() {
            return pattern != null ? pattern.pattern() : null;
        }

        public boolean matches(final String value) {
            if (pattern == null) {
                return true;
            } else {
                return pattern.matcher(value).matches();
            }
        }

        @Override
        public String getValue() {
            if (pattern != null) {
                return "{" + name + ":" + pattern + "}";
            } else {
                return "{" + name + "}";
            }
        }
    }

    static class CurlyBraceTokenizer {

        private final List<String> tokens = new ArrayList<String>();
        private int tokenIdx;

        CurlyBraceTokenizer(final String string) {
            boolean outside = true;
            int level = 0;
            int lastIdx = 0;
            int idx;
            for (idx = 0; idx < string.length(); idx++) {
                if (string.charAt(idx) == '{') {
                    if (outside) {
                        if (lastIdx < idx) {
                            tokens.add(string.substring(lastIdx, idx));
                        }
                        lastIdx = idx;
                        outside = false;
                    } else {
                        level++;
                    }
                } else if (string.charAt(idx) == '}' && !outside) {
                    if (level > 0) {
                        level--;
                    } else {
                        if (lastIdx < idx) {
                            tokens.add(string.substring(lastIdx, idx + 1));
                        }
                        lastIdx = idx + 1;
                        outside = true;
                    }
                }
            }
            if (lastIdx < idx) {
                tokens.add(string.substring(lastIdx, idx));
            }
        }

        public static boolean insideBraces(final String token) {
            return token.charAt(0) == '{' && token.charAt(token.length() - 1) == '}';
        }

        public static String stripBraces(final String token) {
            if (insideBraces(token)) {
                return token.substring(1, token.length() - 1);
            } else {
                return token;
            }
        }

        public boolean hasNext() {
            return tokens.size() > tokenIdx;
        }

        public String next() {
            if (hasNext()) {
                return tokens.get(tokenIdx++);
            } else {
                throw new IllegalStateException("no more elements");
            }
        }
    }
}
