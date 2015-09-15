package org.dvaske.gradle.git

import org.gradle.api.Project

class GitBuildInfoExtension
{
    final static String NAME = 'gitBuildInfo'

    GitBuildInfoExtension( final Project project )
    {

    }

    static GitBuildInfoExtension get( final Project project )
    {
        return project.extensions.getByType( GitBuildInfoExtension )
    }
}
