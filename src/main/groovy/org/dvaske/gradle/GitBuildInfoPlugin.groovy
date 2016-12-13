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
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging

class GitBuildInfo implements Plugin<Project> {

    def logger = Logging.getLogger(this.class)

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
            FileRepositoryBuilder frBuilder = new FileRepositoryBuilder();
            def gitDir = new File(project.projectDir.path)
            //logger.debug("Looking for git dir in project ${project.name}: ${gitDir}")

            Repository repo = frBuilder.findGitDir(gitDir) // scan from the project dir
                    .build();

            //logger.debug("Found git repository at ${repo.workTree} for $project.name")

            def ObjectId head = repo.resolve('HEAD')
            //logger.debug("HEAD: $head")
            if (head) {
                def gitCommit = head.toString(head)
                project.ext.gitCommit = gitCommit
                def gitBranch = repo.getBranch()
                //logger.debug("gitBranch $gitBranch")
                if (gitBranch == gitCommit){
                    // Try to resolve the branch from all refs
                    def branch = getRefs(head, repo)
                    //logger.debug("branch: $branch")
                    if (branch) {
                        project.ext.gitBranch = branch.toString().replace('[', '').replace(']','')
                    }
                } else {
                    project.ext.gitBranch = gitBranch
                }

                def gitRemote = repo.getConfig().getString("remote", "origin", "url")
                //logger.debug("gitRemote: $gitRemote")
                project.ext.gitRemote = gitRemote
                project.ext.gitHead = head.name

                DescribeCommand describe = new Git(repo).describe()
                describe.setLong(true)
                project.ext.gitDescribeInfo = describe.call()
                repo.close()
                //logger.debug("Git repository info: HEAD: $project.gitHead, describe: project.gitDescribeInfo")
            }
            // If 'git describe' returns null, i.e. not tag found, set NA
            if (!project.gitDescribeInfo) {
                project.ext.gitDescribeInfo = NA
            }
        } catch (IllegalArgumentException ex) {
            logger.info("Git repository not found for $project.name")
            //ignore - no git repo
        }
    }
}
