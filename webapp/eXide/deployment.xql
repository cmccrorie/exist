(:
 :  eXist Open Source Native XML Database
 :  Copyright (C) 2011 The eXist Project
 :  http://exist-db.org
 :
 :  This program is free software; you can redistribute it and/or
 :  modify it under the terms of the GNU Lesser General Public License
 :  as published by the Free Software Foundation; either version 2
 :  of the License, or (at your option) any later version.
 :
 :  This program is distributed in the hope that it will be useful,
 :  but WITHOUT ANY WARRANTY; without even the implied warranty of
 :  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 :  GNU Lesser General Public License for more details.
 :
 :  You should have received a copy of the GNU Lesser General Public
 :  License along with this library; if not, write to the Free Software
 :  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 :
 :  $Id$
 :)
xquery version "1.0";

(:~ 
    Edit the expath and repo app descriptors.
    Functions to read, update the descriptors and deploy an app.
:)
    
declare namespace expath="http://expath.org/ns/pkg";
declare namespace repo="http://exist-db.org/xquery/repo";

declare variable $app-root := request:get-attribute("app-root");

declare function local:select-option($value as xs:string, $current as xs:string?, $label as xs:string) {
    <option value="{$value}">
    { if (exists($current) and $value eq $current) then attribute selected { "selected" } else (), $label }
    </option>
};

declare function local:get-app-root($collection as xs:string) {
    if (doc(concat($collection, "/expath-pkg.xml"))) then
        $collection
    else if ($collection ne "/db") then
        let $parent := replace($collection, "^(.*)/[^/]+$", "$1")
        return
            local:get-app-root($parent)
    else
        ()
};

declare function local:store-expath($collection as xs:string?) {
    let $descriptor :=
        <package xmlns="http://expath.org/ns/pkg"
            name="{request:get-parameter('name', ())}" abbrev="{request:get-parameter('abbrev', ())}"
            version="{request:get-parameter('version', ())}" spec="1.0">
            <title>{request:get-parameter("title", ())}</title>
        </package>
    return
        xmldb:store($collection, "expath-pkg.xml", $descriptor, "text/xml")
};

declare function local:store-repo($collection as xs:string?) {
    let $descriptor :=
        <meta xmlns="http://exist-db.org/xquery/repo">
            <description>
            {
                let $desc := request:get-parameter("description", ())
                return
                    if ($desc) then $desc else request:get-parameter("title", ())
            }
            </description>
            {
                for $author in request:get-parameter("author", ())
                return
                    <author>{$author}</author>
            }
            <website>{request:get-parameter("website", ())}</website>
            <status>{request:get-parameter("status", ())}</status>
            <license>GNU-LGPL</license>
            <copyright>true</copyright>
            <type>application</type>
            <target>{request:get-parameter("target", ())}</target>
            <prepare>{request:get-parameter("prepare", ())}</prepare>
            <finish>{request:get-parameter("finish", ())}</finish>
            {
                if (request:get-parameter("owner", ())) then
                    <permissions user="{request:get-parameter('owner', ())}" 
                        password="{request:get-parameter('password', ())}" 
                        group="{request:get-parameter('group', ())}" 
                        mode="{request:get-parameter('mode', ())}"/>
                else
                    ()
            }
        </meta>
    return
        xmldb:store($collection, "repo.xml", $descriptor, "text/xml")
};

declare function local:mkcol-recursive($collection, $components) {
    if (exists($components)) then
        let $newColl := concat($collection, "/", $components[1])
        return (
            xmldb:create-collection($collection, $components[1]),
            local:mkcol-recursive($newColl, subsequence($components, 2))
        )
    else
        ()
};

declare function local:mkcol($path) {
    let $path := if (starts-with($path, "/db/")) then substring-after($path, "/db/") else $path
    return
        local:mkcol-recursive("/db", tokenize($path, "/"))
};

declare function local:create-collection($collection as xs:string) {
    let $target := collection($collection)
    return
        if ($target) then
            $target
        else
            local:mkcol($collection)
};

declare function local:get-template($name as xs:string) {
    if (starts-with($app-root, "xmldb:")) then
        doc(concat($app-root, "/templates/", $name))
    else
        let $url := request:get-url()
        let $base := replace($url, "^(.*/)[^/]+$", "$1")
        let $templateURI := concat($base, "templates/", $name)
        let $log := util:log("DEBUG", ("Retrieving template: ", $templateURI))
        let $response :=
            httpclient:get(xs:anyURI($templateURI), false(), ())
        let $log := util:log("DEBUG", ("DATA: ", $response))
        return
            if ($response/httpclient:body/@type = "xml") then
                $response/httpclient:body/*
            else
                util:base64-decode($response/httpclient:body/string())
};

declare function local:store-templates($collection as xs:string?) {
    let $colConfig := concat("/db/system/config", $collection)
    let $modulesCol := concat($collection, "/modules")
    let $resourcesCol := concat($collection, "/resources")
    return (
        xmldb:store($collection, "collection.xconf", local:get-template("collection.xconf.tmpl"), "application/xml"),
        local:mkcol($modulesCol),
        xmldb:store($modulesCol, "view.xql", local:get-template("view.xql.tmpl"), "application/xquery"),
        xmldb:store($modulesCol, "config.xqm", local:get-template("config.xqm.tmpl"), "application/xquery"),
        local:mkcol(concat($collection, "/resources")),
        xmldb:store($resourcesCol, "style.css", local:get-template("style.css.tmpl"), "text/css"),
        xmldb:store($collection, "index.html", local:get-template("index.html.tmpl"), "text/html"),
        xmldb:store($collection, "pre-install.xql", local:get-template("pre-install.xql.tmpl"), "application/xquery"),
        xmldb:store($collection, "controller.xql", local:get-template("controller.xql.tmpl"), "application/xquery")
    )
};

declare function local:store($collection as xs:string?, $expathConf as element()?, $repoConf as element()?) {
    if (not($collection)) then
        error(QName("http://exist-db.org/xquery/sandbox", "missing-collection"), "collection parameter missing")
    else
        let $create := local:create-collection($collection)
        return
            (local:store-expath($collection), local:store-repo($collection),local:store-templates($collection))
};

declare function local:view($collection as xs:string?, $expathConf as element()?, $repoConf as element()?) {
        <form>
            {
                if ($collection) then (
                    <input type="hidden" name="collection" value="{$collection}"/>,
                    <h3>App collection: {$collection}</h3>
                ) else
                    ()
            }
            <fieldset>
                <legend>Application Properties</legend>
                <ol>
                    {
                        if (not($collection)) then
                            <li>
                                <div class="hint">The source collection for the application's code. For testing purposes, this
                                should be different from the target collection.</div>
                                <input type="text" name="collection" size="40"/>
                                <label for="collection">Source Collection:</label>
                            </li>
                        else
                            ()
                    }
                    <li>
                        <div class="hint">The collection where the app will be installed. Should normally be different from 
                        the source collection.</div>
                        <input type="text" name="target" value="{$repoConf/repo:target}" size="40"/>
                        <label for="target">Target collection:</label>
                    </li>
                    <li><hr/></li>
                    <li>
                        <div class="hint">The name of the package. This must be a URI.</div>
                        <input type="text" name="name" value="{if ($expathConf) then $expathConf/@name else 'http://exist-db.org/apps/'}" size="40"/>
                        <label for="name">Name:</label>
                    </li>
                    <li>
                        <div class="hint">A short name for the app. This will be the name of the collection into which
                        the app is installed.</div>
                        <input type="text" name="abbrev" value="{$expathConf/@abbrev}" size="25"/>
                        <label for="abbrev">Abbreviation:</label>
                    </li>
                    <li>
                        <div class="hint">A descriptive title for the application.</div>
                        <input type="text" name="title" value="{$expathConf/expath:title}" size="40"/>
                        <label for="title">Title:</label>
                    </li>
                    <li>
                        <input type="text" name="version" value="{if ($expathConf) then $expathConf/@version else '0.1'}" size="10"/>
                        <label for="version">Version:</label>
                    </li>
                    <li>
                    {
                        let $status := $repoConf/repo:status/string()
                        return
                            <select name="status">
                                { local:select-option("alpha", $status, "Alpha") }
                                { local:select-option("beta", $status, "Beta") }
                                { local:select-option("stable", $status, "Stable") }
                            </select>
                    }
                        <label for="status">Status:</label>
                    </li>
                    <li><hr/></li>
                    <li>
                        <div class="hint">Optional: name of an XQuery script which will be run <b>before</b> the
                        application is installed. Use this to create users, index configurations and the like.</div>
                        <input type="text" name="prepare" value="{if ($repoConf) then $repoConf/repo:prepare else 'pre-install.xql'}" size="40"/>
                        <label for="prepare">Pre-install XQuery:</label>
                    </li>
                    <li>
                        <div class="hint">Optional: name of an XQuery script which will be run <b>after</b> the
                        application was installed.</div>
                        <input type="text" name="finish" value="{$repoConf/repo:finish}" size="40"/>
                        <label for="finish">Post-install XQuery:</label>
                    </li>
                </ol>
            </fieldset>
            <fieldset>
                <legend>Description</legend>
                <ol>
                    <li>
                        <div class="hint">The author(s) of the application.</div>
                        <label for="author">Author:</label>
                        <ul class="author-repeat">
                        {
                            if (empty($repoConf)) then
                                <li class="repeat"><input type="text" name="author" size="25"/></li>
                            else
                                for $author in $repoConf/repo:author
                                return
                                    <li class="repeat"><input type="text" name="author" value="{$author}" size="25"/></li>
                        }
                            <li><button id="author-add-trigger">Add</button><button id="author-remove-trigger">Remove</button></li>
                        </ul>
                    </li>
                    <li>
                        <div class="hint">A longer description of the application.</div>
                        <textarea name="description" cols="40">{$repoConf/repo:description/text()}</textarea>
                        <label for="description">Description:</label>
                    </li>
                    <li>
                        <div class="hint">Link to the author's website.</div>
                        <input type="text" name="website" value="{$repoConf/repo:website}" size="40"/>
                        <label for="website">Website:</label>
                    </li>
                </ol>
            </fieldset>
            <fieldset>
                <legend>Default Permissions</legend>
                
                <p>Default permissions applied to all resources uploaded into the database. To set
                non-default permissions on particular resources, use a post-install script.</p>
                {
                    let $owner := $repoConf/repo:permissions/@user
                    let $password := $repoConf/repo:permissions/@password
                    let $group := $repoConf/repo:permissions/@group
                    let $mode := $repoConf/repo:permissions/@mode
                    return
                        <ol>
                            <li>
                                <input type="text" name="owner" value="{$owner}" size="20"/>
                                <label for="owner">Owner:</label>
                            </li>
                            <li>
                                <input type="password" name="password" value="{$password}" size="20"/>
                                <label for="owner">Password:</label>
                            </li>
                            <li>
                                <input type="text" name="group" value="{$group}" size="20"/>
                                <label for="owner">Group:</label>
                            </li>
                            <li>
                                <input type="text" name="mode" value="{if ($mode) then $mode else '0444'}" size="4"/>
                                <label for="mode">Mode:</label>
                            </li>
                        </ol>
                }
            </fieldset>
        </form>
};

declare function local:package($collection as xs:string, $expathConf as element()) {
    let $name := concat($expathConf/@abbrev, "-", $expathConf/@version, ".xar")
    let $xar := compression:zip(xs:anyURI($collection), true(), $collection)
    let $mkcol := local:mkcol("/db/system/repo")
    return
        xmldb:store("/db/system/repo", $name, $xar, "application/zip")
};

declare function local:deploy($collection as xs:string, $expathConf as element(),
    $repoConf as element()) {
    let $null := util:declare-option("exist:serialize", "method=json media-type=application/json")
    let $port := request:get-server-port()
    let $pkg := local:package($collection, $expathConf)
    let $url := concat('http://localhost:',$port,'/exist/rest',$pkg)
    let $null := (
        repo:remove($expathConf/@name),
        repo:install($url),
        repo:deploy($expathConf/@name)
    )
    return
        <info>{substring-after($repoConf/repo:target, "/db/")}</info>
};

declare function local:get-info-from-descriptor($collection as xs:string) {
    let $expathConf := doc(concat($collection, "/expath-pkg.xml"))/expath:package
    let $repoConf := doc(concat($collection, "/repo.xml"))/repo:meta
    let $user := request:get-attribute("xquery.user")
    let $auth := if ($user) then xmldb:is-admin-user($user) else false()
    return
        <info xmlns:json="http://json.org" root="{$collection}" abbrev="{$expathConf/@abbrev}">
            <target>{$repoConf/repo:target/string()}</target>
            <deployed>{$repoConf/repo:deployed/string()}</deployed>
            <isAdmin json:literal="true">{$auth}</isAdmin>
        </info>
};

declare function local:get-info($collection as xs:string) {
    let $null := util:declare-option("exist:serialize", "method=json media-type=application/json")
    let $root := local:get-app-root($collection)
    return
        if ($root) then
            local:get-info-from-descriptor($root)
        else
            <info/>
};

let $collectionParam := request:get-parameter("collection", ())
let $collection :=
    if ($collectionParam) then
        let $root := local:get-app-root($collectionParam)
        return
            if ($root) then $root else $collectionParam
    else
        ()
let $info := request:get-parameter("info", ())
let $deploy := request:get-parameter("deploy", ())
let $expathConf := if ($collection) then collection($collection)/expath:package else ()
let $repoConf := if ($collection) then collection($collection)/repo:meta else ()
let $abbrev := request:get-parameter("abbrev", ())
return
    if ($info) then
        local:get-info($info)
    else if ($deploy) then
        local:deploy($collection, $expathConf, $repoConf)
    else if ($abbrev) then
        local:store($collection, $expathConf, $repoConf)
    else
        local:view($collection, $expathConf, $repoConf)