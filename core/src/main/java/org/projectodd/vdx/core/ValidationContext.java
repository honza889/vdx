/*
 * Copyright 2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.projectodd.vdx.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.projectodd.vdx.core.schema.SchemaElement;
import org.projectodd.vdx.core.schema.SchemaPathGate;
import org.projectodd.vdx.core.schema.SchemaPathPrefixProvider;
import org.projectodd.vdx.core.schema.SchemaWalker;
import org.xml.sax.SAXParseException;

public class ValidationContext {
    public ValidationContext(final URL document, final List<URL> schemas) throws IOException {
        this.document = document;
        this.docWalker = new DocWalker(document);
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(document.openStream(), detectCharset(document)))) {
            this.lines = reader.lines().collect(Collectors.toList());
        }

        final Set<String> xmlnses = Util.extractXMLNS(this.lines);
        for (URL url : schemas) {
            if (Util.providesXMLNS(xmlnses, url)) {
                this.schemas.add(url);
            }
        }
    }

    public ValidationContext prefixProvider(final SchemaPathPrefixProvider provider) {
        this.prefixProvider = provider;

        return this;
    }

    public ValidationContext pathGate(final SchemaPathGate gate) {
        this.pathGate = gate;

        return this;
    }

    public int documentLineCount() {
        return this.lines.size();
    }

    public List<String> documentLines() {
        return Collections.unmodifiableList(this.lines);
    }

    public List<String> extractLines(final int start, final int end) {
        final List<String> ret = new ArrayList<>();
        for (int idx = start; idx < end; idx++) {
            ret.add(this.lines.get(idx));
        }

        return ret;
    }

    public ErrorHandler.HandledResult handle(ValidationError error) {
        final ErrorHandler.HandledResult result = error.type().handler().handle(this, error);

        if (result.isPossiblyMalformed() &&
                !this.docWalker.valid()) {
            @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
            final SAXParseException ex = this.docWalker.validationFailure();
            final ErrorHandler.HandledResult validationResult =
                    new ErrorHandler.HandledResult(ex.getLineNumber(), ex.getColumnNumber(), null);
            validationResult.addPrimaryMessage(I18N.Key.PASSTHRU, Util.stripPeriod(ex.getLocalizedMessage()));
            result.addSecondaryMessage(I18N.Key.MALFORMED_XML, Util.documentName(this.document));
            result.addSecondaryResult(validationResult);
        }

        return result;
    }

    public List<List<SchemaElement>> alternateElementsForAttribute(final String attribute) {
        return alternateElements(true, el -> el.attributes().contains(attribute));
    }

    public List<List<SchemaElement>> alternateElementsForElement(final QName element) {
        return alternateElements(false, el -> el.qname().equals(element));
    }

    private List<List<SchemaElement>> alternateElements(final boolean includeValue, final Function<SchemaElement, Boolean> pred) {
        return schemaTree().pathsToValue(includeValue, pred)
                .stream()
                .filter(this::allowPath)
                .map(this::schemaPathWithPrefix)
                .collect(Collectors.toList());
    }

    private boolean allowPath(List<SchemaElement> path) {
        return this.pathGate.allowPath(path.stream()
                                               .map(SchemaElement::qname)
                                               .collect(Collectors.toList()),
                                       this);
    }

    public List<SchemaElement> schemaPathWithPrefix(final List<SchemaElement> path) {
        if (this.prefixProvider == null) {
            this.prefixProvider = (p, __) -> {
                final List<List<DocElement>> prefixPaths = documentTree().pathsToValue(e -> e.name().equals(p.get(0).getLocalPart()));

                if (!prefixPaths.isEmpty()) {
                    return prefixPaths.get(0)
                            .stream()
                            .map(e -> QName.valueOf(e.name()))
                            .collect(Collectors.toList());
                }

                return Collections.emptyList();
            };
        }

        final List<QName> prefix = this.prefixProvider.prefixFor(path.stream()
                                                                       .map(SchemaElement::qname)
                                                                       .collect(Collectors.toList()),
                                                                 this);
        if (prefix != null && !prefix.isEmpty()) {
            final List<SchemaElement> fullPath = new ArrayList<>();
            fullPath.addAll(prefix.stream()
                                    .map(SchemaElement::new)
                                    .collect(Collectors.toList()));
            fullPath.addAll(path);

            return fullPath;
        }

        return path;
    }

    public Set<String> attributesForElement(final List<SchemaElement> path) {
        Tree<SchemaElement> tree = schemaTree();
        final Deque<SchemaElement> pathStack = new ArrayDeque<>(path);

        while (tree != null && !pathStack.isEmpty()) {
            final SchemaElement cur = pathStack.pop();
            tree = tree.children().stream()
                    .filter(t -> t.value().qname().equals(cur.qname()))
                    .findFirst()
                    .orElse(null);
        }

        final Set<String> ret = new HashSet<>();
        if (tree != null && !tree.isRoot()) {
            ret.addAll(tree.value().attributes());
        }

        return ret;
    }

    public Set<SchemaElement> elementsForElement(final List<SchemaElement> path) {
        Tree<SchemaElement> tree = schemaTree();
        final Deque<SchemaElement> pathStack = new ArrayDeque<>(path);

        while (tree != null && !pathStack.isEmpty()) {
            final SchemaElement cur = pathStack.pop();
            tree = tree.children().stream()
                    .filter(t -> t.value().qname().equals(cur.qname()))
                    .findFirst()
                    .orElse(null);
        }

        final Set<SchemaElement> ret = new HashSet<>();
        if (tree != null && !tree.isRoot()) {
            ret.addAll(tree.children().stream()
                               .map(Tree::value)
                               .collect(Collectors.toList()));
        }

        return ret;
    }

    public Position searchForward(final int startLine, final int startCol, final Pattern regex) {
        int loopStartLine = startLine;
        int loopStartCol = startCol;
        while (loopStartLine < this.lines.size()) {
            final String line = this.lines.get(loopStartLine);
            final Matcher matcher = regex.matcher(line);

            if (loopStartCol < line.length() &&
                    matcher.find(loopStartCol)) {

                // return next line, since we're zero indexed here, but 1 indexed for lines
                return new Position(loopStartLine + 1, matcher.start() + 1);
            } else {
                loopStartLine++;
                loopStartCol = 0;
            }
        }

        return null;
    }

    public Position searchBackward(final int startLine, final int startCol, final Pattern regex) {
        int loopStartLine = startLine;
        int loopStartCol = startCol;
        while (loopStartLine >= 0) {
            final String line = this.lines.get(loopStartLine);
            final Matcher matcher = regex.matcher(line);
            if (loopStartCol >= line.length()) {
                loopStartCol = line.length() - 1;
            }

            if (loopStartCol >= 0 &&
                    matcher.find(loopStartCol)) {

                // return next line, since we're zero indexed here, but 1 indexed for lines
                return new Position(loopStartLine + 1, matcher.start() + 1);
            } else if (loopStartCol > 0) {
                loopStartCol--;
            } else {
                loopStartLine--;
                loopStartCol = Integer.MAX_VALUE;
            }
        }

        return null;
    }

    public List<List<DocElement>> pathsToDocElement(final Function<DocElement, Boolean> pred) {
        return documentTree().pathsToValue(true, pred);
    }


    public List<DocElement> pathToDocElement(final Function<DocElement, Boolean> pred) {
        List<List<DocElement>> paths = pathsToDocElement(pred);
        if (!paths.isEmpty()) {

            return paths.get(0);
        }

        return Collections.emptyList();
    }

    public List<DocElement> pathToDocElement(final QName elementName, final Position position) {
        return pathToDocElement(e -> e.qname().equals(elementName) && e.encloses(position));
    }

    public List<List<SchemaElement>> pathsToSchemaElement(final Function<SchemaElement, Boolean> pred) {
        return schemaTree().pathsToValue(true, pred);
    }


    public List<SchemaElement> pathToSchemaElement(final Function<SchemaElement, Boolean> pred) {
        List<List<SchemaElement>> paths = pathsToSchemaElement(pred);
        if (!paths.isEmpty()) {

            return paths.get(0);
        }

        return Collections.emptyList();
    }

    public List<SchemaElement> mapDocPathToSchemaPath(List<DocElement> path) {
        final List<QName> pathQnames = path.stream()
                .map(DocElement::qname)
                .collect(Collectors.toList());
        final QName elementName = pathQnames.get(pathQnames.size() - 1);

        return pathsToSchemaElement(e -> e.qname().equals(elementName)).stream()
                .filter(p -> schemaPathWithPrefix(p).stream()
                        .map(SchemaElement::qname)
                        .collect(Collectors.toList())
                        .equals(pathQnames))
                .findFirst()
                .orElse(Collections.emptyList());
    }

    public List<SchemaElement> mapDocLocationToSchemaPath(final QName elementName, final Position position) {
        return mapDocPathToSchemaPath(pathToDocElement(elementName, position));
    }

    public List<List<DocElement>> docElementSiblings(final List<DocElement> element, final Function<DocElement, Boolean> pred) {
        final List<DocElement> parentPath = element.subList(0, element.size() - 1);

        return pathsToDocElement(pred).stream()
                .filter(p -> !p.equals(element))
                .filter(p -> p.subList(0, p.size() - 1).equals(parentPath))
                .collect(Collectors.toList());
    }

    public List<List<SchemaElement>> schemaElementSiblings(final List<SchemaElement> element) {
        return schemaElementSiblings(element, __ -> true);
    }

    public List<List<SchemaElement>> schemaElementSiblings(final List<SchemaElement> element,
                                                           final Function<SchemaElement, Boolean> pred) {
        final List<SchemaElement> parentPath = element.subList(0, element.size() - 1);

        return pathsToSchemaElement(pred).stream()
                .filter(p -> !p.equals(element))
                .filter(p -> p.subList(0, p.size() - 1).equals(parentPath))
                .collect(Collectors.toList());
    }

    private Tree<DocElement> documentTree() {
        return this.docWalker.walk();
    }

    private Tree<SchemaElement> schemaTree() {
        if (this.walkedSchemas == null) {
            this.walkedSchemas = new SchemaWalker(this.schemas).walk();
        }

        return this.walkedSchemas;
    }

    private static final Map<Charset, byte[]> BOMS = new HashMap<Charset, byte[]>() {{
        put(StandardCharsets.UTF_8, new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
        put(StandardCharsets.UTF_16, new byte[] {(byte)0xFE, (byte)0xFF});
        put(StandardCharsets.UTF_16LE, new byte[] {(byte)0xFF, (byte)0xFE});
    }};

    private static boolean checkBom(final byte[] bom, final byte[] maybeBom) {
        for (int i = 0; i < bom.length; i++) {
            if (bom[i] != maybeBom[i]) {

                return false;
            }
        }

        return true;
    }

    private static Charset checkBom(final byte[] maybeBom) {
        for (Map.Entry<Charset, byte[]> each: BOMS.entrySet()) {
            if (checkBom(each.getValue(), maybeBom)) {

                return each.getKey();
            }
        }

        return null;
    }

    private static Charset detectBom(final URL document) {
        Charset fromBom = null;
        try (final InputStream in = document.openStream()) {
            byte[] maybeBom = new byte[3];
            in.read(maybeBom);
            fromBom = checkBom(maybeBom);
        } catch (IOException ignored) {}

        return fromBom;
    }

    /*
     * Minimal encoding detection. Doesn't validate that the encoding in the xml decl matches the bom.
     * Doesn't inspect bytes other than the bom to guess the encoding. Tries to read the encoding from
     * the xml decl using UTF-8 if no bom is found, which should be safe since everything in the decl
     * should be ascii.
     */
    public static Charset detectCharset(final URL document) {
        Charset charset = detectBom(document);
        if (charset == null) {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(document.openStream(), StandardCharsets.UTF_8))) {
                final String firstLine = reader.readLine();

                charset = detectCharsetFromDecl(firstLine);
            } catch (IOException ignored) {}
        }

        // default to UTF-8
        return charset != null ? charset : StandardCharsets.UTF_8;
    }

    private static Charset detectCharsetFromDecl(final String xmlDecl) {
        final Matcher m = Pattern.compile("\\sencoding\\s*=\\s*['\"](.*?)['\"]").matcher(xmlDecl.trim());
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (UnsupportedCharsetException ignored) {}
        }

        return null;
    }

    private final URL document;
    private final List<String> lines;
    private final List<URL> schemas = new ArrayList<>();
    private final DocWalker docWalker;
    private Tree<SchemaElement> walkedSchemas = null;
    private SchemaPathPrefixProvider prefixProvider = null;
    private SchemaPathGate pathGate = SchemaPathGate.DEFAULT;
}
