/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomee.chatterbox.slack.adapter;

import org.tomitribe.util.IO;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ArgumentsParser {

    private static final char EOF = (char) -1;
    final List<Arguments> commands = new ArrayList<>();
    final List<String> result = new ArrayList<>();
    final InputStream input;

    StringBuilder arg;
    State state = this::continueBare;

    public ArgumentsParser(final String input) {
        this(IO.read(input));
    }

    public ArgumentsParser(final InputStream input) {
        this.input = input;
    }

    public static Arguments[] parse(final String line) {
        final ArgumentsParser parser = new ArgumentsParser(line);
        return parser.parseArgs();
    }

    public Arguments[] parseArgs() {

        char c = EOF;

        while ((c = state.read()) != EOF) {
            if (arg == null) arg = new StringBuilder();
            arg.append(c);
        }

        endCommand();

        return commands.toArray(new Arguments[commands.size()]);
    }

    public void endCommand() {
        if (arg != null) {
            result.add(arg.toString());
            arg.setLength(0);
        }

        if (!result.isEmpty()) {
            commands.add(new Arguments(result.toArray(new String[result.size()])));
            result.clear();
        }
    }

    private char continueBare() {

        final char read = read();

        // Skip passed any white space
        if (isSeparator(read)) {

            // If we were reading an arg, close it
            if (arg != null) {
                result.add(arg.toString());
                arg = null;
            }

            return continueBare();
        }

        if ('|' == read) {
            endCommand();
            return continueBare();
        }

        // We've found something not whitespace
        if (EOF != read && arg == null) {
            arg = new StringBuilder();
        }

        if ('\'' == read) return next(this::inSingleQuotes);
        if ('\"' == read) return next(this::inDoubleQuotes);
        if ('\\' == read) return next(this::escape);

        return read;
    }

    private boolean isSeparator(char read) {
        switch (read) {
            case ' ':
            case '\t':
                return true;
            default:
                return false;
        }
    }

    private char inSingleQuotes() {
        final char read = read();
        if ('\'' == read) return next(this::continueBare);
        return read;
    }

    private char inDoubleQuotes() {
        final char read = read();
        if ('\"' == read) return next(this::continueBare);
        if ('\\' == read) return next(this::escapeInDoubleQuotes);
        return read;
    }

    private char escape() {
        return use(read(), this::continueBare);
    }

    private char escapeInDoubleQuotes() {
        final char read = read();
        switch (read) {
            case '\"':
            case '$':
            case '\\':
            case '`':
                return use(read, this::inDoubleQuotes);
            default:
        }

        return next(flush()
                .state(this::backslash)
                .state(() -> read)
                .state(this::inDoubleQuotes)
                .states());
    }

    private char next(final State state) {
        this.state = state;
        return state.read();
    }

    private char use(char current, final State next) {
        this.state = next;
        return current;
    }

    private char read() {
        try {
            return (char) input.read();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public Seq flush() {
        return new Seq();
    }

    private char backslash() {
        return '\\';
    }

    public interface State {
        char read();
    }

    public class Seq {
        private final List<State> states = new LinkedList<>();

        public Seq state(State next) {
            states.add(next);
            return this;
        }

        public State states() {
            final Iterator<State> iterator = states.iterator();
            return () -> {
                final State next = iterator.next();
                if (iterator.hasNext()) return next.read();
                return next(next);
            };
        }
    }
}
