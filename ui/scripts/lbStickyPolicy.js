(function($, cloudStack) {
  cloudStack.lbStickyPolicy = {
    dialog: function(args) {
      return function(args) {
        var success = args.response.success;
        var context = args.context;
        var network = args.context.networks[0];
        var $item = args.$item;

        var lbService = $.grep(network.service, function(service) {
          return service.name == 'Lb';
        })[0];
        
        var stickinessCapabilities = JSON.parse($.grep(
          lbService.capability,
          function(capability) {
            return capability.name == 'SupportedStickinessMethods';
          }
        )[0].value);

        var baseFields = {
          stickyName: { label: 'Sticky Name', validation: { required: true } },
          name: { label: 'Name', validation: { required: true }, isHidden: true },
          mode: { label: 'Mode', isHidden: true },
          length: { label: 'Length', validation: { required: true }, isHidden: true },
          holdtime: { label: 'Hold Time', validation: { required: true }, isHidden: true },
          tablesize: { label: 'Table size', isHidden: true },
          expire: { label: 'Expire', isHidden: true },
          requestlearn: { label: 'Request-Learn', isBoolean: true, isHidden: true },
          prefix: { label: 'Prefix', isBoolean: true, isHidden: true },
          nocache: { label: 'No cache', isBoolean: true, isHidden: true },
          indirect: { label: 'Indirect', isBoolean: true, isHidden: true },
          postonly: { label: 'Is post-only', isBoolean: true, isHidden: true },
          domain: { label: 'Domain', isBoolean: true, isHidden: true }
        };

        var conditionalFields = {
          methodname: {
            label: 'Stickiness method',
            select: function(args) {
              var $select = args.$select;
              var $form = $select.closest('form');
              var stickyOptions = [];

              stickinessCapabilities.push({ methodname: 'None', paramlist: [] });
              $(stickinessCapabilities).each(function() {
                var stickyCapability = this;

                stickyOptions.push({
                  id: stickyCapability.methodname,
                  description: stickyCapability.methodname
                });
              });

              stickyOptions = stickyOptions.sort(function() {
                return this.id != 'None';
              });

              args.response.success({
                data: stickyOptions
              }, 500);

              $select.change(function() {
                var value = $select.val();
                var showFields = [];
                var targetMethod = $.grep(stickinessCapabilities, function(stickyCapability) {
                  return stickyCapability.methodname == value;
                })[0];
                var visibleParams = $.map(targetMethod.paramlist, function(param) {
                  return param.paramname;
                });

                $select.closest('.form-item').siblings('.form-item').each(function() {
                  var $field = $(this);
                  var id = $field.attr('rel');

                  if ($.inArray(id, visibleParams) > -1) {
                    $field.css('display', 'inline-block');
                    $field.attr('sticky-method', value);
                  } else {
                    $field.hide();
                    $field.attr('sticky-method', null);
                  }
                });

                // Name always is required
                if ($select.val() != 'None') {
                  $select.closest('.form-item').siblings('.form-item[rel=stickyName]')
                    .css('display', 'inline-block');
                }

                $select.closest(':ui-dialog').dialog('option', 'position', 'center');
              });
            }
          }
        };

        var fields = $.extend(conditionalFields, baseFields);

        if (args.data) {
          var populatedFields = $.map(fields, function(field, id) {
            return id;
          });

          $(populatedFields).each(function() {
            var id = this;
            var field = fields[id];
            var dataItem = args.data[id];

            if (field.isBoolean) {
              field.isChecked = dataItem ? true : false;
            } else {
              field.defaultValue = dataItem;
            }
          });
        }

        cloudStack.dialog.createForm({
          form: {
            title: 'Configure Sticky Policy',
            desc: 'Please complete the following fields',
            fields: fields
          },
          after: function(args) {
            // Remove fields not applicable to sticky method
            args.$form.find('.form-item:hidden').remove()
            
            var data = cloudStack.serializeForm(args.$form);

            /* $item indicates that this is an existing sticky rule;
               re-create sticky rule with new parameters */
            if ($item) {
              var $loading = $('<div>').addClass('loading-overlay');

              $loading.prependTo($item);
              cloudStack.lbStickyPolicy.actions.recreate(
                $item.data('multi-custom-data').id,
                $item.data('multi-custom-data').lbRuleID,
                data,
                function() { // Complete
                  $(window).trigger('cloudStack.fullRefresh');
                },
                function(error) { // Error
                  $(window).trigger('cloudStack.fullRefresh');
                }
              );
            } else {
              success({
                data: data
              });
            }
          }
        });
      };
    },

    actions: {
      add: function(lbRuleID, data, complete, error) {
        var stickyURLData = '';
        var stickyParams = $.map(data, function(value, key) {
          return key;
        });

        var notParams = ['methodname', 'stickyName'];

        var index = 0;
        $(stickyParams).each(function() {
          var param = '&param[' + index + ']';
          var name = this.toString();
          var value = data[name];

          if (!value || $.inArray(name, notParams) > -1) return true;
          if (value == 'on') value = true;

          stickyURLData += param + '.name=' + name + param + '.value=' + value;

          index++;

          return true;
        });

        $.ajax({
          url: createURL('createLBStickinessPolicy' + stickyURLData),
          data: {
            lbruleid: lbRuleID,
            name: data.stickyName,
            methodname: data.methodname
          },
          success: function(json) {
            cloudStack.ui.notifications.add(
              {
                desc: 'Add new LB sticky rule',
                section: 'Network',
                poll: pollAsyncJobResult,
                _custom: {
                  jobId: json.createLBStickinessPolicy.jobid
                }
              },
              complete, {},
              error, {}
            );
          },
          error: function(json) {
            complete();
            cloudStack.dialog.notice({ message: parseXMLHttpResponse(json) });
          }
        });
      },
      recreate: function(stickyRuleID, lbRuleID, data, complete, error) {
        var addStickyPolicy = function() {
          cloudStack.lbStickyPolicy.actions.add(
            lbRuleID,
            data,
            complete,
            error
          );
        };
        
        // Delete existing rule
        if (stickyRuleID) {
          $.ajax({
            url: createURL('deleteLBStickinessPolicy'),
            data: {
              id: stickyRuleID
            },
            success: function(json) {
              cloudStack.ui.notifications.add(
                {
                  desc: 'Remove previous LB sticky rule',
                  section: 'Network',
                  poll: pollAsyncJobResult,
                  _custom: {
                    jobId: json.deleteLBstickinessrruleresponse.jobid
                  }
                },
                function() {
                  if (data.methodname != 'None') {
                    addStickyPolicy();
                  } else {
                    complete();
                  }
                }, {},
                error, {}
              );
            },
            error: function(json) {
              cloudStack.dialog.notice({
                message: parseXMLHttpResponse(json)
              });
              error();
            }
          });
        } else if (data.methodname != 'None') {
          addStickyPolicy();
        } else {
          complete();
        }
      }
    }
  };
}(jQuery, cloudStack));