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

package org.dvaske.gradle.git

import org.eclipse.jgit.api.DescribeCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging

class GitBuildInfo implements Plugin<Project> {

    def logger = Logging.getLogger(this.class)
    private GitBuildInfoExtension config

    def void apply(Project project) {
        //this.project = project
        this.config = project.extensions.create(GitBuildInfoExtension.NAME, GitBuildInfoExtension, project)

        //Default values
        project.ext.gitHead = "0000000000000000000000000000000000000000"
        project.ext.gitDescribeInfo = "N/A"

        try {
            FileRepositoryBuilder frBuilder = new FileRepositoryBuilder();
            def gitDir = new File(project.projectDir.path)
            logger.debug("Looking for git dir in project ${project.name}: ${gitDir}")
            Repository repo = frBuilder.findGitDir(gitDir) // scan from the project dir
                    .build();

            logger.info("Found git repository at ${repo.workTree}")
            def head = repo.getRef("HEAD")
            logger.info("HEAD: $head.objectId")

            if (head.objectId != null) {
                project.ext.gitHead = head.objectId.name

                DescribeCommand describe = new Git(repo).describe()
                describe.setLong(true)
                project.ext.gitDescribeInfo = describe.call()
                repo.close()
                logger.info('Git repository info: HEAD: $project.gitHead, describe: project.gitDescribeInfo')
            }
            if (!project.gitDescribeInfo) {
                project.ext.gitDescribeInfo = "N/A"
            }
        } catch (IllegalArgumentException ex) {
            //ignore - no git repo
        }
    }
}

