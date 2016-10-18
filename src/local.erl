%% -*- erlang -*-
%%
%% Cuneiform: A Functional Language for Large Scale Scientific Data Analysis
%%
%% Copyright 2016 Jörgen Brandt, Marc Bux, and Ulf Leser
%%
%% Licensed under the Apache License, Version 2.0 (the "License");
%% you may not use this file except in compliance with the License.
%% You may obtain a copy of the License at
%%
%%    http://www.apache.org/licenses/LICENSE-2.0
%%
%% Unless required by applicable law or agreed to in writing, software
%% distributed under the License is distributed on an "AS IS" BASIS,
%% WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
%% See the License for the specific language governing permissions and
%% limitations under the License.

%% @doc local execution platform.

%% @author Jörgen Brandt <brandjoe@hu-berlin.de>


-module( local ).
-author( "Jorgen Brandt <brandjoe@hu-berlin.de>" ).

-behaviour( cf_cre ).

%% =============================================================================
%% Function Exports
%% =============================================================================

%% cf_cre behaviour callbacks
-export([init/1, handle_submit/8]).

%% API
-export( [stage/7, create_basedir/2, create_workdir/3] ).

%% =============================================================================
%% Includes and Definitions
%% =============================================================================

-include( "cuneiform.hrl" ).

-define( DEFAULT_CONF, #{ basedir => "/tmp/cf",
                          nthread => case erlang:system_info( logical_processors_available ) of unknown -> 1; N -> N end,
                          profiling => false } ).
-define( CONF_FILE, "/usr/local/etc/cuneiform/local.conf" ).

%% =============================================================================
%% Platform Functions
%% =============================================================================

%% init/1
%
-spec init( ManualMap::#{atom() => _} ) -> {ok, {iolist(), pid()}}.

init( ManualMap ) when is_map( ManualMap ) ->

  % create configuration
  Conf = lib_conf:create_conf( ?DEFAULT_CONF, ?CONF_FILE, ManualMap ),
  #{ nthread := NSlot } = Conf,
  BaseDir = create_basedir( maps:get( basedir, Conf ), 1 ),
  {ok, QueueRef} = gen_queue:start_link( NSlot ),
  #{ profiling := Profiling } = Conf, 
  error_logger:info_msg( io_lib:format( "Base directory:    ~s~nNumber of threads: ~p~nProfiling enabled: ~p~n", [BaseDir, NSlot, Profiling] ) ),

  {ok, {BaseDir, QueueRef}}.

%% handle_submit/8
%
-spec handle_submit( Lam, Fa, DataDir, UserInfo, R, LibMap, {BaseDir, QueueRef}, Prof ) ->
  {finished, #{}} | {failed, atom(), pos_integer(), _}
when Lam      :: cre:lam(),
     Fa       :: #{string() => [cre:str()]},
     DataDir  :: string(),
     UserInfo :: _,
     R        :: pos_integer(),
     LibMap   :: #{cf_sem:lang() => [string()]},
     BaseDir  :: iolist(),
     QueueRef :: pid(),
     Prof     :: effi_profiling:profilingsettings().

handle_submit( Lam, Fa, DataDir, _UserInfo, R, LibMap, {BaseDir, QueueRef}, Prof )
when is_tuple( Lam ),
     is_map( Fa ),
     is_integer( R ), R > 0,
     is_list( DataDir ),
     is_map( LibMap ),
     is_list( BaseDir ),
     is_pid( QueueRef ),
     is_tuple( Prof ) ->

  gen_server:cast( QueueRef, {request, self(),
                   {?MODULE, stage, [Lam, Fa, DataDir, R, LibMap, BaseDir, Prof]}} ),

  receive
    Reply -> Reply
  end.

%% create_basedir/2
%
-spec create_basedir( Prefix::string(), I::pos_integer() ) -> iolist().

create_basedir( Prefix, I )
when is_list( Prefix ),
     is_integer( I ), I > 0 ->

  BaseDir = lists:flatten( [Prefix, "-", integer_to_list( I )] ),

  case filelib:is_file( BaseDir ) of
    true  -> create_basedir( Prefix, I+1 );
    false ->
      case filelib:ensure_dir( [BaseDir, "/"] ) of
        {error, Reason} -> error( {Reason, ensure_dir, [BaseDir, "/"]} );
        ok              -> BaseDir
      end
  end.

%% create_workdir/3
%
-spec create_workdir( BaseDir, Work, R ) -> string()
when BaseDir :: string(),
     Work    :: string(),
     R       :: pos_integer().

create_workdir( BaseDir, Work, R )
when is_list( BaseDir ),
     is_list( Work ),
     is_integer( R ), R > 0 ->

  Dir = string:join( [BaseDir, Work, integer_to_list( R )], "/" ),

  % create working directory
  case filelib:ensure_dir( [Dir, "/"] ) of
    {error, R1} -> error( {R1, ensure_dir, [Dir, "/"]} );
    ok          -> Dir
  end.

%% stage/7
%
-spec stage( Lam, Fa, DataDir, R, LibMap, BaseDir, Prof ) -> cre:response()
when Lam     :: cre:lam(),
     Fa      :: #{string() => [cre:str()]},
     DataDir :: string(),
     R       :: pos_integer(),
     LibMap  :: #{cf_sem:lang() => [string()]},
     BaseDir :: iolist(),
     Prof    :: effi_profiling:profilingsettings().

stage( Lam={lam, _LamLine, _LamName, {sign, Lo, Li}, _Body},
       Fa, DataDir, R, LibMap, BaseDir, Prof )
when is_list( Lo ),
     is_list( Li ),
     is_map( Fa ),
     is_list( DataDir ),
     is_integer( R ), R > 0,
     is_map( LibMap ),
     is_list( BaseDir ),
     is_tuple( Prof ) ->


  Dir = create_workdir( BaseDir, ?WORK, R ),
  
  RepoDir = string:join( [BaseDir, ?REPO], "/" ),

  % resolve input files
  Triple1 = lib_refactor:get_refactoring( Li, Fa, Dir, [DataDir, RepoDir], R ),
  {RefactorLst1, MissingLst1, Fa1} = Triple1,

  case MissingLst1 of
    [_|_] -> {failed, precond, R, MissingLst1};
    []    ->

      % link in input files
      lib_refactor:apply_refactoring( RefactorLst1 ),

      % start effi
      case effi:check_run( Lam, Fa1, R, Dir, LibMap, Prof ) of

        {failed, R2, R, Data} -> {failed, R2, R, Data};

        {finished, Sum}       ->

          Ret1 = maps:get( ret, Sum ),

          % resolve output files
          Triple2 = lib_refactor:get_refactoring( Lo, Ret1, RepoDir, [Dir], R ),
          {RefactorLst2, [], Ret2} = Triple2,

          % link out output files
          lib_refactor:apply_refactoring( RefactorLst2 ),

          % update result map
          {finished, Sum#{ret => Ret2}}
      end
  end.