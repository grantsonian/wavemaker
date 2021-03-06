/*
 *  Copyright (C) 2012-2013 VMware, Inc. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.wavemaker.tools.project;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.wavemaker.common.WMRuntimeException;
import com.wavemaker.json.JSON;
import com.wavemaker.json.JSONMarshaller;
import com.wavemaker.json.JSONObject;
import com.wavemaker.json.JSONState;
import com.wavemaker.json.JSONUnmarshaller;
import com.wavemaker.runtime.server.FileUploadResponse;
import com.wavemaker.runtime.server.ServerConstants;
import com.wavemaker.runtime.server.json.JSONUtils;
import com.wavemaker.tools.compiler.ProjectCompiler;
import com.wavemaker.tools.deployment.DeploymentInfo;
import com.wavemaker.tools.deployment.Deployments;
import com.wavemaker.tools.io.File;
import com.wavemaker.tools.io.FilterOn;
import com.wavemaker.tools.io.Folder;
import com.wavemaker.tools.io.Resource;
import com.wavemaker.tools.io.ResourceFilter;
import com.wavemaker.tools.io.ResourceFilterContext;
import com.wavemaker.tools.io.Resources;
import com.wavemaker.tools.io.local.LocalFolder;
import com.wavemaker.tools.io.zip.ZipArchive;

public abstract class AbstractDeploymentManager implements DeploymentManager {

    public static final String EXPORT_DIR_DEFAULT = "export/";

    public static final String PACKAGES_DIR = "packages/";

    public static final String THEMES_DIR = "themes/";

    public static final String LIB_JS_FILE = "lib.js";

    public static final String COMMON_MODULE_PREFIX = "common.packages.";

    public static final String DEPLOYMENTS_FILE = "/deployments.js";

    protected StudioFileSystem fileSystem;

    protected ProjectManager projectManager;

    protected ProjectCompiler projectCompiler;

    protected StudioConfiguration studioConfiguration;

    protected ProjectManager origProjMgr;

    protected LocalFolder tempBuildWebAppRoot = null;

    protected boolean buildInLine;

    protected final StudioFileSystem getFileSystem() {
        return this.fileSystem;
    }

    @Override
    public void setFileSystem(StudioFileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public void setProjectManager(ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    public ProjectManager getProjectManager() {
        return this.projectManager;
    }

    @Override
    public void setProjectCompiler(ProjectCompiler projectCompiler) {
        this.projectCompiler = projectCompiler;
    }

    @Override
    public void setOrigProjMgr(ProjectManager origProjMgr) {
        this.origProjMgr = origProjMgr;
    }

    @Override
    public void setStudioConfiguration(StudioConfiguration studioConfiguration) {
        this.studioConfiguration = studioConfiguration;
    }

    public StudioConfiguration getStudioConfiguration() {
        return this.studioConfiguration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
	public FileUploadResponse importFromZip(MultipartFile file) throws IOException {
	return importFromZip(file,false);
    }
    @Override
	public FileUploadResponse importFromZip(MultipartFile file, boolean isTemplate) throws IOException {
        FileUploadResponse response = new FileUploadResponse();
        org.springframework.core.io.Resource tmpDir = this.projectManager.getTmpDir();

        // Write the zip file to outputFile
        String originalName = file.getOriginalFilename();

        int upgradeIndex = originalName.indexOf("-upgrade-");
        if (upgradeIndex > 0) {
            originalName = originalName.substring(0, upgradeIndex) + ".zip";
        }

        org.springframework.core.io.Resource outputFile = tmpDir.createRelative(originalName);

        OutputStream fos = this.fileSystem.getOutputStream(outputFile);
        FileCopyUtils.copy(file.getInputStream(), fos);

        org.springframework.core.io.Resource finalProjectFolder;
        try {

            // returns null if fails to unzip; need a handler for this...
            org.springframework.core.io.Resource projectFolder = com.wavemaker.tools.project.ResourceManager.unzipFile(this.fileSystem, outputFile);

            // If there is only one folder in what we think is the
            // projectFolder, open that one folder because that must be the real
            // project folder
            // Filter out private folders generated by OS or svn, etc...
            // (__MACOS, .svn, etc...)
            List<org.springframework.core.io.Resource> listings = this.fileSystem.listChildren(projectFolder);
            if (listings.size() == 1) {
                org.springframework.core.io.Resource listing = listings.get(0);
                if (StringUtils.getFilenameExtension(listing.getFilename()) == null && !listing.getFilename().startsWith(".")
                    && !listing.getFilename().startsWith("_")) {
                    projectFolder = listing;
                }
            }

            // Verify that this looks like a project folder we unzipped
            com.wavemaker.tools.project.Project project = new com.wavemaker.tools.project.Project(projectFolder, this.fileSystem);
            org.springframework.core.io.Resource testExistenceFile = project.getWebAppRoot().createRelative("pages/");
            if (!testExistenceFile.exists()) {
                throw new WMRuntimeException("That didn't look like a project folder; if it was, files were missing");
            }

            com.wavemaker.tools.io.File indexhtml = project.getWebAppRootFolder().getFile("index.html");
            String indexstring = project.readFile(indexhtml);
            int endIndex = indexstring.lastIndexOf("({domNode: \"wavemakerNode\"");
            int startIndex = indexstring.lastIndexOf(" ", endIndex);
            String newProjectName = indexstring.substring(startIndex + 1, endIndex);

            // Get a File to point to where we're going to place this imported
            // project
            if(isTemplate){
            	this.fileSystem.getTemplatesFolder().createIfMissing();
            }
            finalProjectFolder = (isTemplate ? this.fileSystem.getTemplatesDir() : this.projectManager.getBaseProjectDir()).createRelative(newProjectName + "/");
	    if (isTemplate && finalProjectFolder.exists()) {
	    	this.fileSystem.deleteFile(finalProjectFolder);
	    }
	    System.out.println("FINAL PATH: " + finalProjectFolder.getURI().toString());
            String finalname = finalProjectFolder.getFilename();
            String originalFinalname = finalname;
            // If there is already a project at that location, rename the
            // project
            int i = -1;
            do {
                i++;
                finalProjectFolder = (isTemplate ? this.fileSystem.getTemplatesDir() : this.projectManager.getBaseProjectDir()).createRelative(finalname + (i > 0 ? "" + i : "") + "/");
            } while (finalProjectFolder.exists());
            finalname = finalProjectFolder.getFilename();

            // OK, now finalname has the name of the new project,
            // finalProjectFolder has the full path to the new project
            // Move the project into the project folder
            this.fileSystem.rename(projectFolder, finalProjectFolder);

            // If we renamed the project (i.e. if i got incremented) then we
            // need to make some corrections
            if (i > 0) {

                // Correction 1: Rename the js file
                com.wavemaker.tools.project.Project finalProject = new com.wavemaker.tools.project.Project(finalProjectFolder, this.fileSystem);
                File jsFile = finalProject.getWebAppRootFolder().getFile(originalFinalname + ".js");
                File newJsFile = finalProject.getWebAppRootFolder().getFile(finalname + ".js");
                jsFile.rename(newJsFile.getName());

                // Correction 2: Change the class name in the js file
                com.wavemaker.tools.project.ResourceManager.ReplaceTextInProjectFile(finalProject, newJsFile, originalFinalname, finalname);

                // Corection3: Change the constructor in index.html
                File index_html = finalProject.getWebAppRootFolder().getFile("index.html");
                com.wavemaker.tools.project.ResourceManager.ReplaceTextInProjectFile(finalProject, index_html, "new " + originalFinalname
                    + "\\(\\{domNode", "new " + finalname + "({domNode");

                // Correction 4: Change the pointer to the js script read in by
                // index.html
                com.wavemaker.tools.project.ResourceManager.ReplaceTextInProjectFile(finalProject, index_html, "\\\"" + originalFinalname
                    + "\\.js\\\"", '"' + finalname + ".js\"");

                // Correction 5: Change the title
                com.wavemaker.tools.project.ResourceManager.ReplaceTextInProjectFile(finalProject, index_html, "\\<title\\>" + originalFinalname
                    + "\\<\\/title\\>", "<title>" + finalname + "</title>");

            }
        } finally {
            // If the user uploaded a zipfile that had many high level folders,
            // they could make a real mess of things,
            // so just purge the tmp folder after we're done
            this.fileSystem.deleteFile(tmpDir);
        }
	    System.out.println("C");
        response.setPath(finalProjectFolder.getFilename());
        response.setError("");
        response.setWidth("");
        response.setHeight("");
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deployClientComponent(String name, String namespace, String data) throws IOException {

        org.springframework.core.io.Resource packagesDir = this.fileSystem.createPath(this.fileSystem.getCommonDir(), PACKAGES_DIR);

        org.springframework.core.io.Resource moduleDir = packagesDir;
        if (namespace != null && namespace.length() > 0) {

            String[] folderList = namespace.split("\\.");
            for (String folder : folderList) {
                moduleDir = this.fileSystem.createPath(moduleDir, folder + "/");
            }
        }

        data = modifyJS(data);

        org.springframework.core.io.Resource jsFile = moduleDir.createRelative(name + ".js");
        FileCopyUtils.copy(data, new OutputStreamWriter(this.fileSystem.getOutputStream(jsFile), ServerConstants.DEFAULT_ENCODING));

        String klass = null;
        if (namespace != null && namespace.length() > 0) {
            klass = namespace + "." + name;
        } else {
            klass = name;
        }
        String moduleString =  COMMON_MODULE_PREFIX + klass;
	writeModuleToLibJs(moduleString);
    }
    public void writeModuleToLibJs(String moduleString) throws IOException {
	moduleString = "\"" + moduleString + "\"";
        boolean found = false;
        org.springframework.core.io.Resource packagesDir = this.fileSystem.createPath(this.fileSystem.getCommonDir(), PACKAGES_DIR);
        org.springframework.core.io.Resource libJsFile = packagesDir.createRelative(LIB_JS_FILE);
        StringBuffer libJsData = new StringBuffer();
        if (libJsFile.exists()) {
            String libJsOriginal = FileCopyUtils.copyToString(new InputStreamReader(libJsFile.getInputStream(), ServerConstants.DEFAULT_ENCODING));
            if (libJsOriginal.indexOf(moduleString) > -1) {
                found = true;
            }
            libJsData.append(libJsOriginal);
        }
        if (!found) {
            libJsData.append("dojo.require(");
            libJsData.append(moduleString);
            libJsData.append(");\n");
        }
        FileCopyUtils.copy(libJsData.toString(), new OutputStreamWriter(this.fileSystem.getOutputStream(libJsFile), ServerConstants.DEFAULT_ENCODING));
    }

    private String modifyJS(String val) {
        boolean foundDojo = false;
        boolean foundWidget = false;
        int startIndx = 0;
        int dojoIndx = -1;
        int compositeIndx = -1;
        int dojoEndIndx = -1;
        int widgetIndx = -1;
        int widgetEndIndx = -1;

        while (!foundDojo) {
            dojoIndx = val.indexOf("dojo.declare", startIndx);
            if (dojoIndx > startIndx) {
                startIndx = dojoIndx + 12;
                compositeIndx = val.indexOf("wm.Composite", startIndx);
                if (compositeIndx >= startIndx) {
                    startIndx = compositeIndx + 12;
                    dojoEndIndx = val.indexOf("});", startIndx);
                    if (dojoEndIndx >= compositeIndx) {
                        foundDojo = true;
                        break;
                    }
                }
            } else {
                break;
            }
        }

        if (!foundDojo) {
            return val;
        }

        startIndx = dojoEndIndx;

        while (!foundWidget) {
            widgetIndx = val.indexOf(".components", startIndx);
            if (widgetIndx > startIndx) {
                startIndx = widgetIndx + 11;
                widgetEndIndx = val.indexOf("wm.publish", startIndx);
                if (widgetEndIndx == -1) {
                    widgetEndIndx = val.indexOf("wm.registerPackage", startIndx);
                }
                if (widgetEndIndx > widgetIndx) {
                    foundWidget = true;
                    break;
                }
            } else {
                break;
            }
        }

        if (!foundWidget) {
            return val;
        }

        boolean done = false;
        startIndx = dojoIndx;
        int indx1;
        String rtn = val.substring(0, dojoIndx);
        while (!done) {
            indx1 = val.indexOf("this.", startIndx);
            if (indx1 > 0) {
                rtn += val.substring(startIndx, indx1);
                if (!validJavaVarPart(val.substring(indx1 - 1, indx1))) {
                    int len = elemLen(val, indx1 + 5, widgetIndx, widgetEndIndx);
                    if (len > 0) {
                        rtn += "this.components.";
                    } else {
                        rtn += "this.";
                    }
                } else {
                    rtn += "this.";
                }
                startIndx = indx1 + 5;
            } else {
                rtn += val.substring(startIndx);
                break;
            }
        }

        return rtn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deployTheme(String themename, String filename, String data) throws IOException {
	themename = themename.substring(1+themename.lastIndexOf("."));
        org.springframework.core.io.Resource themesDir = this.fileSystem.createPath(this.fileSystem.getCommonDir(), THEMES_DIR);

        org.springframework.core.io.Resource moduleDir = themesDir;
        if (themename != null && themename.length() > 0) {
            String[] folderList = themename.split("\\.");
            for (String folder : folderList) {
                moduleDir = this.fileSystem.createPath(moduleDir, folder + "/");
            }
        }
        org.springframework.core.io.Resource outputFile = moduleDir.createRelative(filename);
        FileCopyUtils.copy(data, new OutputStreamWriter(this.fileSystem.getOutputStream(outputFile), ServerConstants.DEFAULT_ENCODING));
    }

    @Override
    public String listThemes() throws IOException {
	StringBuffer s = new StringBuffer();


        // Add common themes
        Folder commonThemes = this.fileSystem.getCommonFolder().getFolder(THEMES_DIR);
        for (Folder theme : commonThemes.list().folders().include(FilterOn.nonHidden())) {
	    Resource r = theme.getFile("button.css");
	    if (s.length() > 0) s.append(",");
	    s.append("'" + theme.getName() + "': {'designer':'" + (r.exists() ? "widgetthemer" : "themedesigner") + "', 'package':'common.themes'}");
        }

        // Add studio themes
        Folder widgetThemes = this.fileSystem.getStudioWebAppRootFolder().getFolder("lib/wm/base/widget/themes/");
        for (Folder theme : widgetThemes.list().folders().exclude(FilterOn.names().starting("default", "wm_studio"))) {
	    Resource r = theme.getFile("button.css");
	    if (s.length() > 0) s.append(",");
	    s.append("'" + theme.getName() + "': {'designer':'" + (r.exists() ? "widgetthemer" : "themedesigner") + "', 'package':'wm.base.widget.themes'}");
        }

        return "{" + s + "}";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void copyTheme(String oldName, String newName) throws IOException {
	String oldNameShort = oldName.substring(1+oldName.lastIndexOf("."));
	String newNameShort = newName.substring(1+newName.lastIndexOf("."));
        org.springframework.core.io.Resource oldFile;
        if (oldName.indexOf("wm.") == 0) {
            oldFile = this.fileSystem.getStudioWebAppRoot().createRelative("lib/wm/base/widget/themes/" + oldNameShort + "/");
        } else {
            oldFile = this.fileSystem.getCommonDir().createRelative(THEMES_DIR + oldNameShort + "/");
        }

        org.springframework.core.io.Resource newFile = this.fileSystem.getCommonDir().createRelative(THEMES_DIR + newNameShort);
        this.fileSystem.copyRecursive(oldFile, newFile, Collections.EMPTY_LIST);

        org.springframework.core.io.Resource cssFile = newFile.createRelative("theme.css");
        com.wavemaker.tools.project.ResourceManager.ReplaceTextInFile(this.fileSystem.getOutputStream(cssFile), cssFile, "\\." + oldNameShort, "."
            + newNameShort);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteTheme(String name) throws IOException {
        this.fileSystem.deleteFile(this.fileSystem.getCommonDir().createRelative(THEMES_DIR + name.substring(1+name.lastIndexOf(".")) + "/"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] listThemeImages(String themename) throws IOException {
        org.springframework.core.io.Resource themesDir;
        if (themename.indexOf("common.") == 0) {
            themesDir = this.fileSystem.getCommonDir().createRelative(THEMES_DIR + themename.substring(1+themename.lastIndexOf(".")) + "/images/");
        } else {
            themesDir = this.fileSystem.getStudioWebAppRoot().createRelative("lib/wm/base/widget/themes/" + themename.substring(1+themename.lastIndexOf(".")) + "/images/");
        }

        String files0[] = getImageFiles(themesDir, null, "repeat,top left,");
        String files1[] = getImageFiles(themesDir, "repeat", "repeat,top left,");
        String files2[] = getImageFiles(themesDir, "repeatx", "repeat-x,top left,");
        String files3[] = getImageFiles(themesDir, "repeaty", "repeat-y,top left,");
        String files4[] = getImageFiles(themesDir, "repeatx_bottom", "repeat-x,bottom left,");
        String files5[] = getImageFiles(themesDir, "repeaty_right", "repeat-y,top right,");

        String[] s = new String[files0.length + files1.length + files2.length + files3.length + files4.length + files5.length];
        int i;
        int index = 0;
        for (i = 0; i < files0.length; i++) {
            s[index++] = files0[i];
        }

        for (i = 0; i < files1.length; i++) {
            s[index++] = files1[i];
        }

        for (i = 0; i < files2.length; i++) {
            s[index++] = files2[i];
        }

        for (i = 0; i < files3.length; i++) {
            s[index++] = files3[i];
        }

        for (i = 0; i < files4.length; i++) {
            s[index++] = files4[i];
        }

        for (i = 0; i < files5.length; i++) {
            s[index++] = files5[i];
        }

        return s;
    }

    private String[] getImageFiles(org.springframework.core.io.Resource themeDir, String folderName, String prepend) {
        org.springframework.core.io.Resource folder;
        try {
            folder = folderName == null ? themeDir : themeDir.createRelative(folderName + "/");
        } catch (IOException ex) {
            throw new WMRuntimeException(ex);
        }
        try {
            List<org.springframework.core.io.Resource> files = this.fileSystem.listChildren(folder, new com.wavemaker.tools.project.ResourceFilter() {

                @Override
                public boolean accept(org.springframework.core.io.Resource file) {
                    String name = file.getFilename().toLowerCase();
                    return name.endsWith(".gif") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                }
            });
            if (files.size() > 0) {
                String[] imageFiles = new String[files.size()];
                for (int i = 0; i < files.size(); i++) {
                    imageFiles[i] = prepend + "url(images/" + (folderName == null ? "" : folderName + "/") + files.get(i).getFilename() + ")";
                }
                return imageFiles;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean validJavaVarPart(String val) {
        int v = val.charAt(0);

        if (v >= 48 && v <= 57 || v >= 64 && v <= 90 || v >= 97 && v <= 122 || v == 95) {
            return true;
        } else {
            return false;
        }
    }

    private int elemLen(String val, int indx, int windx, int windx1) {
        int i;
        for (i = 0; i < windx; i++) {
            if (!validJavaVarPart(val.substring(indx + i, indx + i + 1))) {
                break;
            }
        }

        String item = val.substring(indx, indx + i);
        int j = val.substring(windx, windx1).indexOf(item);
        if (j < 0) {
            return -1;
        }

        int k = val.substring(windx + j + item.length(), windx1).indexOf(":");
        if (k < 0) {
            return -1;
        }

        String s = val.substring(windx + j + item.length(), windx + j + item.length() + k);
        if (s.trim().length() > 0) {
            return -1;
        }

        return i;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean undeployClientComponent(String name, String namespace, boolean removeSource) throws IOException {
        org.springframework.core.io.Resource packagesDir = this.fileSystem.getCommonDir().createRelative(PACKAGES_DIR);
        if (!packagesDir.exists()) {
            return false;
        }

        if (removeSource) {
            org.springframework.core.io.Resource moduleDir = packagesDir;
            List<org.springframework.core.io.Resource> rmDirs = new ArrayList<org.springframework.core.io.Resource>();
            if (namespace != null && namespace.length() > 0) {
                String[] folderList = namespace.split("\\.");
                for (String folder : folderList) {
                    moduleDir = moduleDir.createRelative(folder + "/");
                    rmDirs.add(moduleDir);
                }
            }
            org.springframework.core.io.Resource sourceJsFile = moduleDir.createRelative(name);
            if (sourceJsFile.exists()) {
                this.fileSystem.deleteFile(sourceJsFile);
                for (int i = rmDirs.size() - 1; i > -1; i--) {
                    org.springframework.core.io.Resource rmDir = rmDirs.get(i);
                    if (this.fileSystem.listChildren(rmDir).size() == 0) {
                        this.fileSystem.deleteFile(rmDir);
                    } else {
                        break;
                    }
                }
            }
        }
        org.springframework.core.io.Resource libJsFile = packagesDir.createRelative(LIB_JS_FILE);
        StringBuffer libJsData = new StringBuffer();
        if (libJsFile.exists()) {
            boolean found = false;
            String klass = null;
            if (namespace != null && namespace.length() > 0) {
                klass = namespace + "." + name + "." + name;
            } else {
                klass = name;
            }
            List<String> libJsStringList = FileUtils.readLines(libJsFile.getFile(), ServerConstants.DEFAULT_ENCODING);
            for (int i = 0; i < libJsStringList.size(); i++) {
                String s = libJsStringList.get(i);
                if (s.indexOf("\"" + COMMON_MODULE_PREFIX + klass + "\"") > -1) {
                    found = true;
                } else {
                    libJsData.append(s);
                    libJsData.append("\n");
                }
            }
            FileCopyUtils.copy(libJsData.toString(), new OutputStreamWriter(this.fileSystem.getOutputStream(libJsFile),
                ServerConstants.DEFAULT_ENCODING));
            return found;
        }
        return false;
    }

    @Override
    public List<DeploymentInfo> getDeploymentInfo() {
        Deployments deployments = readDeployments();
        return deployments.forProject(this.projectManager.getCurrentProject().getProjectName());
    }

    /**
     * @param deploymentInfo
     * @return
     */
    @Override
    public String saveDeploymentInfo(DeploymentInfo deploymentInfo) {
        org.springframework.core.io.Resource deploymentsResource;
        Writer writer = null;
        try {
            Deployments deployments = readDeployments();
            deployments.save(this.projectManager.getCurrentProject().getProjectName(), deploymentInfo);

            deploymentsResource = this.fileSystem.getCommonDir().createRelative(DEPLOYMENTS_FILE);
            writer = new OutputStreamWriter(this.fileSystem.getOutputStream(deploymentsResource));
            JSONMarshaller.marshal(writer, deployments, new JSONState(), false, true);
            writer.flush();
        } catch (IOException e) {
            throw new WMRuntimeException("An error occurred while trying to save deployment.", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return deploymentInfo.getDeploymentId();
    }

    /**
     * @param deploymentId
     * @return
     */
    @Override
    public void deleteDeploymentInfo(String deploymentId) {
        org.springframework.core.io.Resource deploymentsResource;
        Writer writer = null;
        try {
            Deployments deployments = readDeployments();
            deployments.remove(this.projectManager.getCurrentProject().getProjectName(), deploymentId);
            deploymentsResource = this.fileSystem.getCommonDir().createRelative(DEPLOYMENTS_FILE);
            writer = new OutputStreamWriter(this.fileSystem.getOutputStream(deploymentsResource));
            JSONMarshaller.marshal(writer, deployments, new JSONState(), false, true);
            writer.flush();
        } catch (IOException e) {
            throw new WMRuntimeException("An error occurred while trying to save deployment.", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected Deployments readDeployments() {
        return readDeployments(this.projectManager.getCurrentProject(), this.fileSystem);
    }

    public static Deployments readDeployments(Project project, StudioFileSystem fileSystem) {
        com.wavemaker.tools.io.File file = fileSystem.getCommonFolder().getFile(DEPLOYMENTS_FILE);
        String content = file.exists() ? file.getContent().asString() : "";
        if (!StringUtils.hasLength(content)) {
            return new Deployments();
        }
        JSON result = JSONUnmarshaller.unmarshal(content);
        Assert.isTrue(result instanceof JSONObject, file.toString() + " is in an unexpected format.");
        return (Deployments) JSONUtils.toBean((JSONObject) result, Deployments.class);
    }

    @Override
    public String exportProject(String zipFileName) {
        Project project = getProjectManager().getCurrentProject();
        Resources<?> export = project.getRootFolder().find();
        export = export.exclude(FilterOn.antPattern("/export/**", "/dist/**", "/webapproot/WEB-INF/classes/**", "/webapproot/WEB-INF/lib/**",
            "/phonegap/**", "/.git/**"));
        export = export.exclude(new ResourceFilter() {

            @Override
            public boolean match(ResourceFilterContext context, Resource resource) {
                return resource instanceof File && resource.getParent().getParent() == null && resource.getName().toLowerCase().endsWith(".xml");
            }
        });
        InputStream inputStream = ZipArchive.compress(export.files());
        File exportFile = project.getRootFolder().getFolder(EXPORT_DIR_DEFAULT).getFile(zipFileName);
        exportFile.getContent().write(inputStream);
        return exportFile.toString().substring(1);
    }

    public String exportMultiFile(String zipFileName, boolean buildProject, boolean buildProjectTemplate, String templateJson, String[] themeList, String[] componentList ) throws IOException {
	Project project = getProjectManager().getCurrentProject();
	Folder tmpFolder = project.getRootFolder().getFolder(EXPORT_DIR_DEFAULT).getFolder("tmp" + new java.util.Date().getTime());
	tmpFolder.createIfMissing();
	File zipFile = project.getRootFolder().getFolder(EXPORT_DIR_DEFAULT).getFile(zipFileName);
	Folder commonFolder = this.fileSystem.getCommonFolder();

	if (buildProject || buildProjectTemplate) {
	    Resources<?> export = project.getRootFolder().find();
	    export = export.exclude(FilterOn.antPattern("/export/**", "/dist/**", "/webapproot/WEB-INF/classes/**", "/webapproot/WEB-INF/lib/**",
							"/phonegap/**", "/.git/**"));

	    export = export.exclude(new ResourceFilter() {
		    @Override
			public boolean match(ResourceFilterContext context, Resource resource) {
			return resource instanceof File && resource.getParent().getParent() == null && resource.getName().toLowerCase().endsWith(".xml");
		    }
		});
	    
	    Folder projectFolder = buildProject ? tmpFolder.getFolder("project") : tmpFolder.getFolder("projecttemplate");
	    projectFolder.createIfMissing();
	    Folder destFolder = projectFolder.getFolder(project.getProjectName());
	    destFolder.createIfMissing();
	    export.files().copyTo(destFolder);
	    if (buildProjectTemplate && templateJson.length() > 0) {
		File templateJsonFile = destFolder.getFile("template.json");
		templateJsonFile.getContent().write(templateJson);
	    }
	}
	
	if (themeList.length > 0) {
	    Folder themesFolder = tmpFolder.getFolder("themes");
	    themesFolder.createIfMissing();

	    for (int i = 0; i < themeList.length; i++) {
		Folder theme = commonFolder.getFolder("themes/" + themeList[i]);
		theme.copyTo(themesFolder);
	    }
	}

	if (componentList.length > 0) {
	    Folder componentDestFolder = tmpFolder.getFolder("components");
	    componentDestFolder.createIfMissing();

	    for (int i = 0; i < componentList.length; i++) {
		Folder componentFolder = commonFolder.getFolder("packages/" + componentList[i]);
		if (componentFolder.exists()) {
		    componentFolder.copyTo(componentDestFolder);
		} else {
		    File componentFile = commonFolder.getFile("packages/" + componentList[i] + ".js");
		    if (componentFile.exists()) {
			componentFile.copyTo(componentDestFolder);
		    }
		}
	    }
	}

        InputStream inputStream = ZipArchive.compress(tmpFolder);
        zipFile.getContent().write(inputStream);
	tmpFolder.delete();
	return zipFile.toString();
    }

    protected LocalFolder getProjectDir(com.wavemaker.tools.project.Project project) {
        return (LocalFolder) project.getRootFolder();
    }

    protected LocalFolder getProjectDir() {
        com.wavemaker.tools.project.Project currentProject = getProjectManager().getCurrentProject();
        if (currentProject == null) {
            throw new WMRuntimeException("Current project must be set");
        }
        return getProjectDir(currentProject);
    }

    protected String getDeployName() {
        return getDeployName(this.projectManager.getCurrentProject());
    }

    protected String getDeployName(com.wavemaker.tools.project.Project project) {
        return project.getProjectName();
    }
}
