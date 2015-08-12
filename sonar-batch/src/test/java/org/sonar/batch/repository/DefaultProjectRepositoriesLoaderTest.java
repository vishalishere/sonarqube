/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.repository;

import org.apache.commons.lang.mutable.MutableBoolean;

import org.sonar.batch.scan.ProjectAnalysisMode;
import org.apache.commons.io.IOUtils;
import org.sonar.batch.bootstrap.WSLoaderResult;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.utils.MessageException;
import org.sonar.batch.bootstrap.AnalysisProperties;
import org.sonar.batch.bootstrap.WSLoader;
import org.sonar.batch.protocol.input.ProjectRepositories;
import org.sonar.batch.protocol.input.QProfile;
import org.sonar.batch.rule.ModuleQProfiles;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultProjectRepositoriesLoaderTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private DefaultProjectRepositoriesLoader loader;
  private WSLoader wsLoader;
  private ProjectAnalysisMode analysisMode;
  private ProjectDefinition project;
  private AnalysisProperties taskProperties;

  @Before
  public void prepare() {
    wsLoader = mock(WSLoader.class);
    analysisMode = mock(ProjectAnalysisMode.class);
    loader = new DefaultProjectRepositoriesLoader(wsLoader, analysisMode);
    loader = spy(loader);
    when(wsLoader.loadString(anyString())).thenReturn(new WSLoaderResult<>("{}", true));
    taskProperties = new AnalysisProperties(Maps.<String, String>newHashMap(), "");
  }

  @Test
  public void passPreviewParameter() {
    addQualityProfile();
    project = ProjectDefinition.create().setKey("foo");
    when(analysisMode.isIssues()).thenReturn(false);
    loader.load(project, taskProperties, null);
    verify(wsLoader).loadString("/batch/project?key=foo&preview=false");

    when(analysisMode.isIssues()).thenReturn(true);
    loader.load(project, taskProperties, null);
    verify(wsLoader).loadString("/batch/project?key=foo&preview=true");
  }

  @Test
  public void deserializeResponse() throws IOException {
    String resourceName = this.getClass().getSimpleName() + "/sample_response.json";
    String response = IOUtils.toString(this.getClass().getResourceAsStream(resourceName));
    when(wsLoader.loadString(anyString())).thenReturn(new WSLoaderResult<>(response, true));
    project = ProjectDefinition.create().setKey("foo");
    MutableBoolean fromCache = new MutableBoolean();
    ProjectRepositories projectRepo = loader.load(project, taskProperties, fromCache);

    assertThat(fromCache.booleanValue()).isTrue();
    assertThat(projectRepo.activeRules().size()).isEqualTo(221);
    assertThat(projectRepo.fileDataByPath("my:project").size()).isEqualTo(11);

  }

  @Test
  public void passAndEncodeProjectKeyParameter() {
    addQualityProfile();
    project = ProjectDefinition.create().setKey("foo bàr");
    loader.load(project, taskProperties, null);
    verify(wsLoader).loadString("/batch/project?key=foo+b%C3%A0r&preview=false");
  }

  @Test
  public void passAndEncodeProfileParameter() {
    addQualityProfile();
    project = ProjectDefinition.create().setKey("foo");
    taskProperties.properties().put(ModuleQProfiles.SONAR_PROFILE_PROP, "my-profile#2");
    loader.load(project, taskProperties, null);
    verify(wsLoader).loadString("/batch/project?key=foo&profile=my-profile%232&preview=false");
  }

  @Test
  public void fail_with_message_exception_when_no_quality_profile() throws Exception {
    thrown.expect(MessageException.class);
    thrown.expectMessage("No quality profiles has been found this project, you probably don't have any language plugin suitable for this analysis.");

    project = ProjectDefinition.create().setKey("foo");
    when(wsLoader.loadString(anyString())).thenReturn(new WSLoaderResult<>(new ProjectRepositories().toJson(), true));

    loader.load(project, taskProperties, null);
  }

  private void addQualityProfile() {
    ProjectRepositories projectRepositories = new ProjectRepositories();
    projectRepositories.addQProfile(new QProfile("key", "name", "language", new Date()));
    when(wsLoader.loadString(anyString())).thenReturn(new WSLoaderResult<>(projectRepositories.toJson(), true));
  }

}
