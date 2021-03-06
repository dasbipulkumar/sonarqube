/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import $ from 'jquery';
import _ from 'underscore';
import Marionette from 'backbone.marionette';
import Template from './templates/issues-layout.hbs';

export default Marionette.LayoutView.extend({
  template: Template,

  regions: {
    filtersRegion: '.search-navigator-filters',
    facetsRegion: '.search-navigator-facets',
    workspaceHeaderRegion: '.search-navigator-workspace-header',
    workspaceListRegion: '.search-navigator-workspace-list',
    workspaceComponentViewerRegion: '.issues-workspace-component-viewer',
    workspaceHomeRegion: '.issues-workspace-home'
  },

  onRender () {
    if (this.options.app.state.get('isContext')) {
      this.$(this.filtersRegion.el).addClass('hidden');
    }
    this.$('.search-navigator').addClass('sticky');
    const top = this.$('.search-navigator').offset().top;
    this.$('.search-navigator-workspace-header').css({ top });
    this.$('.search-navigator-side').css({ top }).isolatedScroll();
  },

  showSpinner (region) {
    return this[region].show(new Marionette.ItemView({
      template: _.template('<i class="spinner"></i>')
    }));
  },

  showComponentViewer () {
    this.scroll = $(window).scrollTop();
    this.$('.issues').addClass('issues-extended-view');
  },

  hideComponentViewer () {
    this.$('.issues').removeClass('issues-extended-view');
    if (this.scroll != null) {
      $(window).scrollTop(this.scroll);
    }
  },

  showHomePage () {
    this.$('.issues').addClass('issues-home-view');
  },

  hideHomePage () {
    this.$('.issues').removeClass('issues-home-view');
  }
});

