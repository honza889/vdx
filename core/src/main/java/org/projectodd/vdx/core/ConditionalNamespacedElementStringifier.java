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

import java.util.function.Predicate;

import org.projectodd.vdx.core.schema.SchemaElement;

public class ConditionalNamespacedElementStringifier implements Stringifier {
    public ConditionalNamespacedElementStringifier(final Predicate<SchemaElement> pred) {
        this.pred = pred;
    }

    @Override
    public boolean handles(Object value) {
        return Stringifier.super.handles(value) &&
                pred.test((SchemaElement)value);
    }

    @Override
    public Class handledClass() {
        return SchemaElement.class;
    }

    @Override
    public String asString(Object value) {
        final SchemaElement el = (SchemaElement)value;

        return String.format("{%s}%s", el.qname().getNamespaceURI(), el.name());
    }

    private final Predicate<SchemaElement> pred;
}
