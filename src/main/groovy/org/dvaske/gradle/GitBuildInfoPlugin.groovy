/*
* git-build-info
* Created by Aske Olsson on 2015-09-15.
* Copyright (c) 2015, Aske Olsson
* All rights reserved.
*
* The MIT License (MIT)
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*
*/

package org.dvaske.gradle

import org.eclipse.jgit.api.DescribeCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.errors.NoWorkTreeException
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class GitBuildInfo implements Plugin<Project> {

    Logger logger = Logging.getLogger(this.class)

    private final static String INITIALSHA= "0000000000000000000000000000000000000000"
    private final static String NA = "N/A"

    private GitBuildInfoExtension config

    def void apply(Project project) {
        //this.project = project
        this.config = project.extensions.create(GitBuildInfoExtension.NAME, GitBuildInfoExtension, project)

        //Default values
        project.ext.gitHead = INITIALSHA
        project.ext.gitDescribeInfo = NA
        project.ext.gitCommit = INITIALSHA
        project.ext.gitBranch = NA
        project.ext.gitRemote = NA

        try {
            def gitDir = new File(project.projectDir.path)

            FileRepositoryBuilder frBuilder = new FileRepositoryBuilder();
            //def gitDir = new File(project.projectDir.path)
            logger.debug("Looking for git dir in project ${project.name}: ${gitDir}")
            Repository repo = frBuilder.findGitDir(gitDir) // scan from the project dir
            .build();
            logger.debug("Found git repository at ${repo.workTree} for $project.name")

            File gitFile = new File(project.rootProject.buildDir.path + "/gitbuildinfo/" + repo.workTree.name)
            if (gitFile.exists()){
                loadValuesFromFile(gitFile, project)
            } else {
                // Find git values

                def ObjectId head = repo.resolve('HEAD')
                logger.debug("HEAD: $head")
                if (head) {
                def gitCommit = head.toString(head)
                project.ext.gitCommit = gitCommit
                def gitBranch = repo.getBranch()
                logger.debug("gitBranch $gitBranch")

                // If we are on a detached head resolve branch:
                if (gitBranch == gitCommit){
                    // Try to resolve the branch from all refs
                    def branch = getRefs(head, repo)
                    logger.debug("branch: $branch")
                    if (branch) {
                        project.ext.gitBranch = branch.toString().replace('[', '').replace(']','')
                    }
                } else {
                    project.ext.gitBranch = gitBranch
                }

                def gitRemote = repo.getConfig().getString("remote", "origin", "url")
                logger.debug("gitRemote: $gitRemote")
                project.ext.gitRemote = gitRemote
                project.ext.gitHead = head.name

                try {
                    DescribeCommand describe = new Git(repo).describe()
                    describe.setLong(true)
                    project.ext.gitDescribeInfo = describe.call()

                } catch (JGitInternalException e) {
                    logger.warn('`git describe` returned an error. Are you in a shallow cloned Git repository?', e)
                }
                repo.close()

                //logger.debug("Git repository info: HEAD: $project.gitHead, describe: project.gitDescribeInfo")
                }
                // If 'git describe' returns null, i.e. not tag found, set NA
                if (!project.gitDescribeInfo) {
                    project.ext.gitDescribeInfo = NA
                }
            writeValuesToFile(gitFile, project)
            }

        } catch (IllegalArgumentException ex) {
            logger.warn("Git repository not found for $project.name")
            //ignore - no git repo
        } catch (NoWorkTreeException ex) {
            logger.warn('Could not find a git repository with worktree for ' + project.name)
            logger.warn('`git worktree is not supported by jgit (yet): ')
            logger.warn('https://bugs.eclipse.org/bugs/show_bug.cgi?id=477475')
            logger.warn('Exception: ' + ex.toString())
        }
    }

    private void writeValuesToFile(File gitFile, Project project){
        // Make sure buildDir in rootproject exists
        if (! project.rootProject.buildDir.exists()){
            project.rootProject.buildDir.mkdir()
        }
        if (! new File(gitFile.parent).exists()){
            new File(gitFile.parent).mkdir()
        }
        logger.debug("Writing gitInfo for ${project.name} in ${gitFile.path}")
        gitFile.withWriter('UTF-8') { writer ->
            writer.write("gitHead=${project.gitHead}\n")
            writer.write("gitDescribeInfo=${project.gitDescribeInfo}\n")
            writer.write("gitCommit=${project.gitCommit}\n")
            writer.write("gitBranch=${project.gitBranch}\n")
            writer.write("gitRemote=${project.gitRemote}\n")
        }
    }

    private void loadValuesFromFile(File gitFile, Project project){
        logger.debug("Loading gitInfo for ${project.name} from ${gitFile.path}")
        Properties properties = new Properties()
        gitFile.withInputStream {
            properties.load(it)
        }
        project.ext.gitHead = properties.gitHead
        project.ext.gitDescribeInfo = properties.gitDescribeInfo
        project.ext.gitCommit = properties.gitCommit
        project.ext.gitBranch = properties.gitBranch
        project.ext.gitRemote = properties.gitRemote
    }

    private Map<ObjectId, List<String>> getAllRefs(FileRepository r) {
        def Map<ObjectId, List<String>> refs = new HashMap<ObjectId, List<String>>();
        def Map<AnyObjectId, Set<Ref>> allRefs = r.getAllRefsByPeeledObjectId();
        for (AnyObjectId id : allRefs.keySet()) {
            List<String> list = new ArrayList<String>();
            for (Ref setRef : allRefs.get(id)) {
                String name = setRef.getName();
                // 'refs/heads/' and 'refs/remote/' in ref name
                list.add(name.replace('refs/heads/', '').replace('refs/remotes/', ''));
            }
            refs.put(id.toObjectId(), list as Set);
        }
        return refs;
    }

    private String getRefs(ObjectId commit, FileRepository repo){
        Map<ObjectId, Set<String>> refs = getAllRefs(repo)
        //return StringUtils.join(refs.get(commit), ',')
        return refs.get(commit)
    }
}
