/*
 * Copyright (C) 2019 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package antibug.doc;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import antibug.doc.builder.SiteBuilder;
import antibug.doc.site.MainPage;
import kiss.I;
import stylist.StyleDeclarable;
import stylist.Stylist;

public class Javadoc extends ModernJavadocProcessor {

    /** The scanned data. */
    public final Data data = new Data();

    /** The site builder. */
    private SiteBuilder site;

    /** Preference */
    public String productName = "Your Product";

    /** PackageName-URL pair. */
    private final Map<String, String> externals = new HashMap();

    /** The internal pacakage names. */
    private final Set<String> internals = new HashSet();

    {
        // built-in external API
        externalDoc("https://docs.oracle.com/en/java/javase/13/docs/api/");
    }

    /**
     * Specifies the URL of the resolvable external document.
     * 
     * @param urls A list of document URL．
     * @return Chainable API.
     */
    public Javadoc externalDoc(String... urls) {
        if (urls != null) {
            for (String url : urls) {
                if (url != null && url.startsWith("http") && url.endsWith("/api/")) {
                    try {
                        I.signal(new URL(url + "overview-tree.html"))
                                .map(I::xml)
                                .retryWhen(e -> e.delay(200, TimeUnit.MILLISECONDS).take(20))
                                .flatIterable(xml -> xml.find(".horizontal a"))
                                .to(xml -> {
                                    externals.put(xml.text(), url);
                                });
                    } catch (MalformedURLException e) {
                        throw I.quiet(e);
                    }
                }
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize(ModernDocletModel model) {
        // build CSS
        I.load(Javadoc.class);
        Stylist.pretty()
                .importNormalizeStyle()
                .styles(I.findAs(StyleDeclarable.class))
                .formatTo(model.output().file("main.css").asJavaPath());

        site = SiteBuilder.root(model.output()).guard("index.html", "main.js", "main.css");
        internals.addAll(model.findSourcePackages());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void process(TypeElement root) {
        data.add(new ClassInfo(root, new TypeResolver(externals, internals, root)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void process(PackageElement root) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void process(ModuleElement root) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void complete(ModernDocletModel model) {
        // sort data
        data.modules.sort(Comparator.naturalOrder());
        data.packages.sort(Comparator.naturalOrder());
        data.types.sort(Comparator.naturalOrder());

        // after care
        data.connectSubType();

        // build HTML
        site.buildHTML("javadoc.html", new MainPage(this, null));

        for (ClassInfo info : data.types) {
            site.buildHTML("types/" + info.packageName + "." + info.name + ".html", new MainPage(this, info));
        }
    }
}
