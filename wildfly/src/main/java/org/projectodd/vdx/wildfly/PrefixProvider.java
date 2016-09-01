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

package org.projectodd.vdx.wildfly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;

import org.projectodd.vdx.core.Util;
import org.projectodd.vdx.core.ValidationContext;
import org.projectodd.vdx.core.schema.SchemaPathPrefixProvider;

class PrefixProvider implements SchemaPathPrefixProvider {

    @Override
    public List<QName> prefixFor(final List<QName> path, final ValidationContext ctx) {
        if (prefix.isEmpty()) {
            final QName rootElement = Util.extractFirstElement(ctx.documentLines());
            prefix.add(rootElement);
            prefix.add(new QName(rootElement.getNamespaceURI(), "profile"));
        }

        if (this.prefix.get(0).equals(path.get(0))) {

            return Collections.emptyList();
        }

        return Collections.unmodifiableList(prefix);
    }

    private final List<QName> prefix = new ArrayList<>();
}
